-- =============================================================================
-- Migration 005 — OTP gateway device flow (FCM -> OTPSender app -> SIM SMS)
--
-- WHY THIS EXISTS
--   The backend already delivers OTPs through a cloud SMS provider (MSG91 /
--   Twilio / Fast2SMS / WhatsApp / SMTP). This migration adds an ADDITIONAL
--   delivery path: the backend creates an `otp_sms_requests` row, pushes an
--   FCM "SMS_REQUEST" notification to the registered OTPSender Android app
--   (com.littlebridge.otpsender), and that gateway app fetches the request
--   and sends the SMS through the device's own SIM, then reports SENT/FAILED
--   back. The existing provider delivery + OTP logging is NOT removed.
--
--   Two new tables back the 6 gateway APIs:
--     otp_gateway_devices  — registered OTPSender gateway devices (FCM tokens)
--     otp_sms_requests     — one row per outbound SMS handed off to a gateway
--
-- HOW TO RUN
--   Supabase -> SQL Editor -> paste this whole file -> Run.
--   100% SAFE TO RE-RUN: every statement is guarded with IF NOT EXISTS, so
--   running it against a database that already has the tables / columns /
--   indexes is a harmless no-op and NEVER raises an error.
--
-- The column names / types below match server/.../db/Tables.kt
-- (OtpGatewayDevicesTable, OtpSmsRequestsTable) EXACTLY so the Exposed
-- mapping lines up with the real Postgres schema.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- otp_gateway_devices
--   Registered OTPSender Android gateway devices. The backend picks the
--   latest active device whose last_seen_at is within 5 minutes and pushes
--   an FCM SMS_REQUEST to it. If no device is available the request stays
--   PENDING and is recoverable via GET /api/v1/gateway/pending.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.otp_gateway_devices (
  id            uuid NOT NULL,
  device_id     varchar(128) NOT NULL,          -- Android-wide unique id reported by the app
  device_name   varchar(128) NULL,
  fcm_token     text NOT NULL,                  -- FCM registration token for push
  app_version   varchar(32) NULL,
  last_seen_at  timestamp without time zone NOT NULL DEFAULT now(),
  is_active     boolean NOT NULL DEFAULT true,
  battery_level integer NULL,                   -- 0..100
  network_type  varchar(16) NULL,               -- wifi | cellular | none
  created_at    timestamp without time zone NOT NULL DEFAULT now(),
  updated_at    timestamp without time zone NOT NULL DEFAULT now(),
  CONSTRAINT otp_gateway_devices_pkey PRIMARY KEY (id)
) TABLESPACE pg_default;

-- Re-assert every column so the migration also "heals" a partially-created
-- table. Each is a no-op when the column already exists.
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS device_id     varchar(128) NOT NULL;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS device_name   varchar(128) NULL;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS fcm_token     text NOT NULL;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS app_version   varchar(32) NULL;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS last_seen_at  timestamp without time zone NOT NULL DEFAULT now();
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS is_active     boolean NOT NULL DEFAULT true;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS battery_level integer NULL;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS network_type  varchar(16) NULL;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS created_at    timestamp without time zone NOT NULL DEFAULT now();
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS updated_at    timestamp without time zone NOT NULL DEFAULT now();

-- One row per physical device (device_id is the Android unique id).
CREATE UNIQUE INDEX IF NOT EXISTS ux_otp_gw_devices_device_id
  ON public.otp_gateway_devices (device_id);
-- Gateway selection scans active devices ordered by last_seen_at.
CREATE INDEX IF NOT EXISTS ix_otp_gw_devices_active_seen
  ON public.otp_gateway_devices (is_active, last_seen_at);

-- -----------------------------------------------------------------------------
-- otp_sms_requests
--   One row per outbound SMS the backend hands off to an OTPSender gateway.
--   Created in PENDING when /api/v1/otp/send runs; the gateway app transitions
--   it through PROCESSING -> SENT / FAILED as it dispatches the SMS from its
--   own SIM and reports status back. `provider` records which path delivered
--   it ("gateway" vs the legacy provider name) so the operator can see the
--   dual-delivery overlap.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.otp_sms_requests (
  id                 uuid NOT NULL,
  phone_number       varchar(32) NOT NULL,
  message            text NOT NULL,
  status             varchar(16) NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSING | SENT | FAILED
  provider           varchar(32) NULL,                         -- "gateway" / "msg91" / "twilio" / ...
  assigned_device_id uuid NULL,                                -- FK otp_gateway_devices.id (set when a gateway is picked)
  error_message      text NULL,
  created_at         timestamp without time zone NOT NULL DEFAULT now(),
  sent_at            timestamp without time zone NULL,
  delivered_at       timestamp without time zone NULL,
  CONSTRAINT otp_sms_requests_pkey PRIMARY KEY (id)
) TABLESPACE pg_default;

ALTER TABLE public.otp_sms_requests ADD COLUMN IF NOT EXISTS phone_number       varchar(32) NOT NULL;
ALTER TABLE public.otp_sms_requests ADD COLUMN IF NOT EXISTS message            text NOT NULL;
ALTER TABLE public.otp_sms_requests ADD COLUMN IF NOT EXISTS status             varchar(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE public.otp_sms_requests ADD COLUMN IF NOT EXISTS provider           varchar(32) NULL;
ALTER TABLE public.otp_sms_requests ADD COLUMN IF NOT EXISTS assigned_device_id uuid NULL;
ALTER TABLE public.otp_sms_requests ADD COLUMN IF NOT EXISTS error_message      text NULL;
ALTER TABLE public.otp_sms_requests ADD COLUMN IF NOT EXISTS created_at         timestamp without time zone NOT NULL DEFAULT now();
ALTER TABLE public.otp_sms_requests ADD COLUMN IF NOT EXISTS sent_at            timestamp without time zone NULL;
ALTER TABLE public.otp_sms_requests ADD COLUMN IF NOT EXISTS delivered_at       timestamp without time zone NULL;

-- Recovery scan (GET /api/v1/gateway/pending) filters on status; admin
-- dashboard orders by created_at.
CREATE INDEX IF NOT EXISTS ix_otp_sms_requests_status
  ON public.otp_sms_requests (status);
CREATE INDEX IF NOT EXISTS ix_otp_sms_requests_created
  ON public.otp_sms_requests (created_at);

-- Soft FK from otp_sms_requests.assigned_device_id -> otp_gateway_devices.id.
-- Kept optional (ON DELETE SET NULL) so retiring a gateway device never
-- strands or deletes the audit trail of SMS it already handled.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'fk_otp_sms_requests_assigned_device'
      AND conrelid = 'public.otp_sms_requests'::regclass
  ) THEN
    ALTER TABLE public.otp_sms_requests
      ADD CONSTRAINT fk_otp_sms_requests_assigned_device
      FOREIGN KEY (assigned_device_id)
      REFERENCES public.otp_gateway_devices (id)
      ON DELETE SET NULL;
  END IF;
END$$;
