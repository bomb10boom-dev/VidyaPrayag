-- =============================================================================
-- Migration: setup_otp_gateway
-- Branch:    feature/setup_notification
-- Subject:   OTPSender SMS-gateway integration
--              • otp_gateway_devices — registry of OTPSender Android gateways
--              • sms_requests        — the SMS-delivery request queue
--
-- WHY THIS EXISTS
--   The OTPSender Android app is an SMS GATEWAY. Instead of paying an SMS
--   vendor, the backend:
--     1. generates + stores the OTP (auth_otps — unchanged),
--     2. creates an sms_requests row (status = pending),
--     3. locates an active OTPSender device (otp_gateway_devices),
--     4. pushes an FCM data-message to that device (separate Firebase project),
--     5. returns the request_id.
--   The OTPSender phone sends the SMS from its own SIM and reports back via
--   POST /api/v1/gateway/requests/{requestId}/status.
--
-- HOW TO RUN
--   Supabase / Render Postgres -> SQL Editor -> paste this whole file -> Run.
--   100% SAFE TO RE-RUN: every statement is guarded with IF NOT EXISTS, so
--   running it against a database that already has the tables / columns /
--   indexes is a harmless no-op and NEVER raises an error.
--
-- COLUMN FIDELITY
--   The column names / types / nullability / defaults below match
--   server/src/main/kotlin/com.littlebridge.enrollplus/db/Tables.kt
--   (OtpGatewayDevicesTable + SmsRequestsTable) EXACTLY so the Exposed ORM
--   mapping lines up with the real Postgres schema.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- otp_gateway_devices
-- ---------------------------------------------------------------------------
--   id            uuid PK
--   device_id     varchar(128) NOT NULL — OTPSender install id (natural key)
--   device_name   varchar(128) NULL — human label (e.g. "Office Pixel")
--   fcm_token     text NOT NULL — OTPSender FCM token (separate Firebase project)
--   app_version   varchar(64) NULL — OTPSender BuildConfig.VERSION_NAME
--   is_active     boolean default true — soft-deactivated when retired
--   battery_level integer NULL — 0..100, reported by heartbeat
--   network_type  varchar(32) NULL — wifi | cellular | …, reported by heartbeat
--   last_seen_at  timestamp NULL — bumped by register + heartbeat (liveness)
--   created_at    timestamp — first registration time
--   updated_at    timestamp — last metadata refresh
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.otp_gateway_devices (
  id             uuid          NOT NULL,
  device_id      varchar(128)  NOT NULL,
  device_name    varchar(128)  NULL,
  fcm_token      text          NOT NULL,
  app_version    varchar(64)   NULL,
  is_active      boolean       NOT NULL DEFAULT true,
  battery_level  integer       NULL,
  network_type   varchar(32)   NULL,
  last_seen_at   timestamp without time zone NULL,
  created_at     timestamp without time zone NOT NULL DEFAULT now(),
  updated_at     timestamp without time zone NOT NULL DEFAULT now(),
  CONSTRAINT otp_gateway_devices_pkey PRIMARY KEY (id)
) TABLESPACE pg_default;

-- Re-assert every column so the migration also "heals" a partially-created
-- table. Each is a no-op when the column already exists with the same shape.
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS device_id      varchar(128)  NOT NULL;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS device_name    varchar(128)  NULL;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS fcm_token      text          NOT NULL;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS app_version    varchar(64)   NULL;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS is_active      boolean       NOT NULL DEFAULT true;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS battery_level  integer       NULL;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS network_type   varchar(32)   NULL;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS last_seen_at   timestamp without time zone NULL;
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS created_at     timestamp without time zone NOT NULL DEFAULT now();
ALTER TABLE public.otp_gateway_devices ADD COLUMN IF NOT EXISTS updated_at     timestamp without time zone NOT NULL DEFAULT now();

-- Indexes (must match OtpGatewayDevicesTable.init{} in Tables.kt EXACTLY).
-- A device_id is globally unique (one OTPSender install). Re-registering the
-- same device_id is resolved by the repository as an UPDATE — never a dup.
CREATE UNIQUE INDEX IF NOT EXISTS ux_otp_gateway_devices_device_id
  ON public.otp_gateway_devices (device_id);

-- Fast selection of the freshest active gateway for dispatch
-- (WHERE is_active = true AND last_seen_at >= now() - interval '5 minutes'
--  ORDER BY last_seen_at DESC LIMIT 1).
CREATE INDEX IF NOT EXISTS ix_otp_gateway_devices_active_seen
  ON public.otp_gateway_devices (is_active, last_seen_at);

-- ---------------------------------------------------------------------------
-- sms_requests
-- ---------------------------------------------------------------------------
--   id            uuid PK
--   request_id    varchar(64) NOT NULL — public id (UUID string), gateway key
--   phone_number  varchar(32) NOT NULL — E.164 destination
--   otp           varchar(12) NULL — plaintext code the gateway sends
--   message       text NOT NULL — full SMS body
--   status        varchar(16) default 'pending' — pending|dispatched|sent|failed
--   device_id     varchar(128) NULL — assigned OTPSender device (null while pending)
--   error_message text NULL — populated when status = failed
--   purpose       varchar(24) default 'login' — login | signup | …
--   created_at    timestamp
--   updated_at    timestamp
--   dispatched_at timestamp NULL — when the FCM data-message was pushed
--   sent_at       timestamp NULL — when the gateway reported SENT
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.sms_requests (
  id             uuid          NOT NULL,
  request_id     varchar(64)   NOT NULL,
  phone_number   varchar(32)   NOT NULL,
  otp            varchar(12)   NULL,
  message        text          NOT NULL,
  status         varchar(16)   NOT NULL DEFAULT 'pending',
  device_id      varchar(128)  NULL,
  error_message  text          NULL,
  purpose        varchar(24)   NOT NULL DEFAULT 'login',
  created_at     timestamp without time zone NOT NULL DEFAULT now(),
  updated_at     timestamp without time zone NOT NULL DEFAULT now(),
  dispatched_at  timestamp without time zone NULL,
  sent_at        timestamp without time zone NULL,
  CONSTRAINT sms_requests_pkey PRIMARY KEY (id)
) TABLESPACE pg_default;

ALTER TABLE public.sms_requests ADD COLUMN IF NOT EXISTS request_id     varchar(64)   NOT NULL;
ALTER TABLE public.sms_requests ADD COLUMN IF NOT EXISTS phone_number   varchar(32)   NOT NULL;
ALTER TABLE public.sms_requests ADD COLUMN IF NOT EXISTS otp            varchar(12)   NULL;
ALTER TABLE public.sms_requests ADD COLUMN IF NOT EXISTS message        text          NOT NULL;
ALTER TABLE public.sms_requests ADD COLUMN IF NOT EXISTS status         varchar(16)   NOT NULL DEFAULT 'pending';
ALTER TABLE public.sms_requests ADD COLUMN IF NOT EXISTS device_id      varchar(128)  NULL;
ALTER TABLE public.sms_requests ADD COLUMN IF NOT EXISTS error_message  text          NULL;
ALTER TABLE public.sms_requests ADD COLUMN IF NOT EXISTS purpose        varchar(24)   NOT NULL DEFAULT 'login';
ALTER TABLE public.sms_requests ADD COLUMN IF NOT EXISTS created_at     timestamp without time zone NOT NULL DEFAULT now();
ALTER TABLE public.sms_requests ADD COLUMN IF NOT EXISTS updated_at     timestamp without time zone NOT NULL DEFAULT now();
ALTER TABLE public.sms_requests ADD COLUMN IF NOT EXISTS dispatched_at  timestamp without time zone NULL;
ALTER TABLE public.sms_requests ADD COLUMN IF NOT EXISTS sent_at        timestamp without time zone NULL;

-- Indexes (must match SmsRequestsTable.init{} in Tables.kt EXACTLY).
-- request_id is the public lookup key for the gateway status callbacks.
CREATE UNIQUE INDEX IF NOT EXISTS ux_sms_requests_request_id
  ON public.sms_requests (request_id);

-- Recovery query: pending/dispatched requests, oldest first
-- (GET /api/v1/gateway/pending).
CREATE INDEX IF NOT EXISTS ix_sms_requests_status_created
  ON public.sms_requests (status, created_at);

-- ---------------------------------------------------------------------------
-- updated_at auto-touch triggers
-- ---------------------------------------------------------------------------
-- The repositories set updated_at on every write, but we also install
-- Postgres-side triggers so any ad-hoc SQL UPDATE (e.g. a manual deactivation
-- in the editor) keeps the column honest.
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.touch_otp_gateway_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_otp_gateway_devices_touch_updated_at ON public.otp_gateway_devices;
CREATE TRIGGER trg_otp_gateway_devices_touch_updated_at
  BEFORE UPDATE ON public.otp_gateway_devices
  FOR EACH ROW
  EXECUTE FUNCTION public.touch_otp_gateway_updated_at();

DROP TRIGGER IF EXISTS trg_sms_requests_touch_updated_at ON public.sms_requests;
CREATE TRIGGER trg_sms_requests_touch_updated_at
  BEFORE UPDATE ON public.sms_requests
  FOR EACH ROW
  EXECUTE FUNCTION public.touch_otp_gateway_updated_at();

-- End of migration.
