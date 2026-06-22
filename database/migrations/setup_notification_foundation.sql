-- =============================================================================
-- Migration: setup_notification_foundation
-- Branch:    feature/setup_notification
-- Subject:   device_tokens table — FCM/APNs device registration registry
--
-- WHY THIS EXISTS
--   The notification foundation PR (feat(notification): setup FCM
--   infrastructure and device token registration) introduces server-side push
--   delivery via the Firebase Admin SDK. To fan a notification out to a user
--   the backend must know every FCM registration token currently held by that
--   user's devices. This table is that registry.
--
--   A user may own MULTIPLE devices (phone + tablet + another phone). We
--   intentionally do NOT overwrite previous tokens — every active token stays
--   valid and is fanned out to independently. Registration is keyed by the
--   (token) value itself; re-registering an existing token merely refreshes
--   its metadata + last_seen_at and re-asserts is_active = true. The
--   DeviceTokenRepository upsert on the server enforces this invariant.
--
-- HOW TO RUN
--   Supabase -> SQL Editor -> paste this whole file -> Run.
--   100% SAFE TO RE-RUN: every statement is guarded with IF NOT EXISTS, so
--   running it against a database that already has the table / columns /
--   indexes / constraints is a harmless no-op and NEVER raises an error.
--
-- COLUMN FIDELITY
--   The column names / types / nullability / defaults below match
--   server/src/main/kotlin/com.littlebridge.enrollplus/db/Tables.kt
--   (DeviceTokensTable) EXACTLY so the Exposed ORM mapping lines up with the
--   real Postgres schema. The shared client DTO (RegisterDeviceTokenRequest)
--   and the server DTO round-trip through this schema unchanged.
--
-- COLUMN GLOSSARY
--   id            uuid PK — Supabase-style UUID primary key (Exposed UUIDTable).
--   school_id     uuid NULL — optional tenant scope (admin/teacher tokens may
--                 be scoped to a school; parent tokens may be unscoped).
--   user_id       uuid NOT NULL — owner app_users.id; the recipient of pushes.
--   token         text NOT NULL — the FCM registration token (one per install).
--   platform      varchar(16) default 'android' — android | ios | web.
--   app_version   varchar(64) NULL — diagnostics (BuildConfig.VERSION_NAME).
--   device_model  varchar(128) NULL — diagnostics (Build.MANUFACTURER + MODEL).
--   is_active     boolean default true — soft-deactivated when FCM reports the
--                 token invalid (UNREGISTERED / registration-token-not-registered).
--   created_at    timestamp — first registration time.
--   updated_at    timestamp — last metadata refresh.
--   last_seen_at  timestamp NULL — bumped on every re-registration / push.
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.device_tokens (
  id            uuid          NOT NULL,
  school_id     uuid          NULL,                       -- optional tenant scope
  user_id       uuid          NOT NULL,                   -- owner app_users.id
  token         text          NOT NULL,                   -- FCM registration token
  platform      varchar(16)   NOT NULL DEFAULT 'android', -- android | ios | web
  app_version   varchar(64)   NULL,                       -- diagnostics
  device_model  varchar(128)  NULL,                       -- diagnostics
  is_active     boolean       NOT NULL DEFAULT true,      -- false when FCM says invalid
  created_at    timestamp without time zone NOT NULL DEFAULT now(),
  updated_at    timestamp without time zone NOT NULL DEFAULT now(),
  last_seen_at  timestamp without time zone NULL,         -- bumped on re-register / push
  CONSTRAINT device_tokens_pkey PRIMARY KEY (id)
) TABLESPACE pg_default;

-- Re-assert every column so the migration also "heals" a partially-created
-- table (e.g. an earlier draft missing some columns). Each is a no-op when the
-- column already exists with the same shape.
ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS school_id     uuid          NULL;
ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS user_id       uuid          NOT NULL;
ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS token         text          NOT NULL;
ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS platform      varchar(16)   NOT NULL DEFAULT 'android';
ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS app_version   varchar(64)   NULL;
ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS device_model  varchar(128)  NULL;
ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS is_active     boolean       NOT NULL DEFAULT true;
ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS created_at    timestamp without time zone NOT NULL DEFAULT now();
ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS updated_at    timestamp without time zone NOT NULL DEFAULT now();
ALTER TABLE public.device_tokens ADD COLUMN IF NOT EXISTS last_seen_at  timestamp without time zone NULL;

-- ---------------------------------------------------------------------------
-- Indexes (must match DeviceTokensTable.init{} in Tables.kt EXACTLY)
-- ---------------------------------------------------------------------------

-- A token string is globally unique (FCM issues one per install). Re-registering
-- the same token from a different user (rare, e.g. a handed-down device) is
-- resolved by the repository as an UPDATE on this unique index — never a dup.
CREATE UNIQUE INDEX IF NOT EXISTS ux_device_tokens_token
  ON public.device_tokens (token);

-- Fast lookup of every ACTIVE token for a user (multi-device fan-out). The
-- NotificationService queries WHERE user_id = ? AND is_active = true to build
-- the FCM multicast batches.
CREATE INDEX IF NOT EXISTS ix_device_tokens_user_active
  ON public.device_tokens (user_id, is_active);

-- ---------------------------------------------------------------------------
-- Foreign keys (deferred / optional — only added when the parent row exists)
-- ---------------------------------------------------------------------------
-- user_id -> app_users.id : keeps token ownership honest. Kept optional via
--   ON DELETE CASCADE so deleting an app_users row purges their tokens
--   automatically (no orphaned device registry entries).
-- school_id -> schools.id : tenant scope integrity. ON DELETE SET NULL so a
--   school being deleted does not strand the user's tokens.
-- ---------------------------------------------------------------------------

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_name = 'fk_device_tokens_user_id'
      AND table_name = 'device_tokens'
  ) THEN
    ALTER TABLE public.device_tokens
      ADD CONSTRAINT fk_device_tokens_user_id
      FOREIGN KEY (user_id) REFERENCES public.app_users (id)
      ON DELETE CASCADE;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_name = 'fk_device_tokens_school_id'
      AND table_name = 'device_tokens'
  ) THEN
    ALTER TABLE public.device_tokens
      ADD CONSTRAINT fk_device_tokens_school_id
      FOREIGN KEY (school_id) REFERENCES public.schools (id)
      ON DELETE SET NULL;
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- updated_at auto-touch trigger
-- ---------------------------------------------------------------------------
-- Exposed's timestamp("updated_at") column is written by the repository on
-- every upsert, but we also install a Postgres-side trigger so any ad-hoc SQL
-- UPDATE (e.g. a manual deactivation in the Supabase editor) keeps the column
-- honest without callers having to remember to set it.
-- ---------------------------------------------------------------------------

DROP TRIGGER IF EXISTS trg_device_tokens_touch_updated_at ON public.device_tokens;

CREATE OR REPLACE FUNCTION public.touch_device_tokens_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_device_tokens_touch_updated_at
  BEFORE UPDATE ON public.device_tokens
  FOR EACH ROW
  EXECUTE FUNCTION public.touch_device_tokens_updated_at();

-- End of migration.
