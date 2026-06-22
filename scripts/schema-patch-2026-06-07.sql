-- =============================================================================
-- schema-patch-2026-06-07.sql
-- VidyaPrayag — Schema Gap Patch (PostgreSQL / Supabase)
-- -----------------------------------------------------------------------------
-- WHEN TO RUN
--   Apply this file BEFORE seed-2026-06-07.sql on any database provisioned via
--   the canonical recipe (docs/db/PROVISION.sql). The recipe today creates
--   36 of the 38 tables registered in server/.../db/Tables.kt; this patch
--   adds the two missing ones so the seed has somewhere to insert and so the
--   server's validateSchema() boot gate passes in strict (AUTO_CREATE_TABLES=
--   false) mode.
--
-- IDEMPOTENT
--   Every statement uses IF NOT EXISTS / IF EXISTS or DO blocks. Running this
--   file twice is a no-op. No data is destroyed.
--
-- WHAT'S IN HERE
--   PATCH-001  CREATE TABLE scholarships                    (RE-AUDIT RA-05)
--   PATCH-002  CREATE TABLE scholarship_applications        (RE-AUDIT RA-05)
--   PATCH-003  OPTIONAL fee_records.status CHECK enum       (RE-AUDIT RA-11)
--              -- shipped commented-out; uncomment only after confirming all
--              -- existing rows already use the canonical values.
--   VERIFY     Final SELECTs confirming both tables exist and are empty.
--
-- COLUMN SHAPES
--   Mirror server/.../db/Tables.kt:754-781 exactly. Column types match the
--   base schema's "timestamp without time zone" convention (Exposed
--   timestamp() default). Defaults match the Kotlin .default(...) values.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- PATCH-001 — scholarships table (re-audit RA-05)
--
-- Source of truth: server/src/main/kotlin/com.littlebridge.enrollplus/db/Tables.kt
--                  object ScholarshipsTable : UUIDTable("scholarships", "id") { … }
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scholarships (
    id            uuid                        PRIMARY KEY DEFAULT gen_random_uuid(),
    title         text                        NOT NULL,
    description   text                        NOT NULL,
    amount        text                        NOT NULL,
    time_left     text                        NOT NULL DEFAULT '',
    category      varchar(48)                 NOT NULL DEFAULT 'Merit Based',
    is_critical   boolean                     NOT NULL DEFAULT false,
    position      integer                     NOT NULL DEFAULT 0,
    is_active     boolean                     NOT NULL DEFAULT true,
    created_at    timestamp without time zone NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at    timestamp without time zone NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

COMMENT ON TABLE  scholarships             IS 'Catalogue of scholarships shown on the parent Scholarships screen. Global (not school-scoped). Added by schema-patch-2026-06-07 to close RE-AUDIT RA-05.';
COMMENT ON COLUMN scholarships.amount      IS 'Display string, e.g. "Rs. 45,000". Not numeric — surfaces formatted to UI as-is.';
COMMENT ON COLUMN scholarships.time_left   IS 'Display string, e.g. "3d : 12h". Computed/refreshed elsewhere.';

CREATE INDEX IF NOT EXISTS ix_scholarships_active_position
    ON scholarships (is_active, position);

-- -----------------------------------------------------------------------------
-- PATCH-002 — scholarship_applications table (re-audit RA-05)
--
-- Source of truth: server/src/main/kotlin/com.littlebridge.enrollplus/db/Tables.kt
--                  object ScholarshipApplicationsTable : UUIDTable(...) { … }
--
-- parent_id references app_users(id). The Exposed model does NOT declare an
-- FK (RE-AUDIT RA-18), but at the SQL layer we add ON DELETE CASCADE so that
-- when an app_users row is deleted the orphan applications do not strand.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scholarship_applications (
    id           uuid                        PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id    uuid                        NOT NULL,
    institution  text                        NOT NULL,
    program      text                        NOT NULL,
    status       varchar(24)                 NOT NULL DEFAULT 'Received',
    icon_name    varchar(32)                 NOT NULL DEFAULT 'school',
    position     integer                     NOT NULL DEFAULT 0,
    created_at   timestamp without time zone NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at   timestamp without time zone NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

-- Add the FK in a DO block so re-runs do not fail when it already exists.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_scholarship_applications_parent'
    ) THEN
        ALTER TABLE scholarship_applications
            ADD CONSTRAINT fk_scholarship_applications_parent
            FOREIGN KEY (parent_id) REFERENCES app_users(id) ON DELETE CASCADE;
    END IF;
END$$;

COMMENT ON TABLE  scholarship_applications             IS 'Parent-scoped applications shown next to the catalogue. parent_id is app_users.id. Added by schema-patch-2026-06-07 to close RE-AUDIT RA-05.';
COMMENT ON COLUMN scholarship_applications.status      IS 'Received | Under Review | Shortlisted (free-text; no CHECK).';
COMMENT ON COLUMN scholarship_applications.icon_name   IS 'UI glyph key; e.g. school | trophy | book.';

CREATE INDEX IF NOT EXISTS ix_scholarship_applications_parent_position
    ON scholarship_applications (parent_id, position);

-- -----------------------------------------------------------------------------
-- PATCH-003 (OPTIONAL, COMMENTED-OUT) — fee_records.status CHECK enum
--
-- RE-AUDIT RA-11: fee_records.status is a free-form varchar today, but every
-- consumer (ParentFeesRouting.kt) branches on a finite set: DUE | PAID |
-- OVERDUE. Uncomment to enforce the constraint AT DDL — but verify first that
-- no legacy row violates it, otherwise the ALTER will fail.
--
-- Pre-flight:
--   SELECT DISTINCT status FROM fee_records ORDER BY 1;
--   -- expected output: just DUE, PAID, OVERDUE (case-sensitive)
-- -----------------------------------------------------------------------------
-- DO $$
-- BEGIN
--     IF NOT EXISTS (
--         SELECT 1 FROM pg_constraint
--         WHERE conname = 'ck_fee_records_status_enum'
--     ) THEN
--         ALTER TABLE fee_records
--             ADD CONSTRAINT ck_fee_records_status_enum
--             CHECK (status IN ('DUE','PAID','OVERDUE'));
--     END IF;
-- END$$;

COMMIT;

-- =============================================================================
-- VERIFICATION — run after COMMIT. Both tables must report 'exists'; the FK
-- must report 'present'; the optional CHECK status is informational.
-- =============================================================================
SELECT 'scholarships'             AS object,
       CASE WHEN to_regclass('public.scholarships')             IS NOT NULL THEN 'exists' ELSE 'MISSING' END AS state
UNION ALL
SELECT 'scholarship_applications',
       CASE WHEN to_regclass('public.scholarship_applications') IS NOT NULL THEN 'exists' ELSE 'MISSING' END
UNION ALL
SELECT 'fk_scholarship_applications_parent',
       CASE WHEN EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_scholarship_applications_parent')
            THEN 'present' ELSE 'MISSING' END
UNION ALL
SELECT 'ck_fee_records_status_enum (optional)',
       CASE WHEN EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_fee_records_status_enum')
            THEN 'present' ELSE 'not-enabled' END;

-- =============================================================================
-- END schema-patch-2026-06-07.sql
-- =============================================================================
