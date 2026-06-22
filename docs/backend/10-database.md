# 10 — Database Schema Reference

**Files:**
- `server/src/main/kotlin/com.littlebridge.enrollplus/db/DatabaseFactory.kt`
- `server/src/main/kotlin/com.littlebridge.enrollplus/db/UserTable.kt`
- `server/src/main/kotlin/com.littlebridge.enrollplus/db/Tables.kt`
- `server/src/main/kotlin/com.littlebridge.enrollplus/db/Seed.kt`

Library: [JetBrains Exposed](https://github.com/JetBrains/Exposed) 0.50.0
Pool:    HikariCP

---

## Engines

- **Dev / local:** SQLite at `data.db` in CWD. Zero configuration.
- **Production:** Postgres — read from `DATABASE_URL` env (e.g. Supabase URI).

Switching happens automatically in `DatabaseFactory.createPostgresDataSource()`
when `DATABASE_URL` is non-blank.

## Auto-migration

Every Table object listed in `DatabaseFactory.allTables` is passed to
`SchemaUtils.createMissingTablesAndColumns(*allTables)` at boot. New columns
added to an existing Table are ALTER-TABLE-added in place; new tables are
CREATE-TABLE'd. **Dropping or renaming columns is NOT handled** — manage that
manually if/when it happens.

## Tables

### users  (`UserTable.kt`)
| col | type | notes |
|---|---|---|
| id                | UUID  | PK, autoGenerate |
| name              | VARCHAR(255) | |
| contact           | VARCHAR(255) | UNIQUE — email OR phone |
| email             | VARCHAR(255) | nullable |
| phone             | VARCHAR(50)  | nullable |
| password_hash     | VARCHAR(255) | nullable (SHA-256) |
| role              | VARCHAR(50)  | ADMIN \| PARENT \| TEACHER |
| is_phone_verified | BOOL | default false |
| is_email_verified | BOOL | default false |
| profile_pic       | TEXT | nullable |
| profile_completed | BOOL | default false |
| refresh_token     | TEXT | nullable |

### schools  (`SchoolTable`)
Owned by exactly one ADMIN user (`owner_user_id`). Becomes the "tenant"
for all other rows tagged with `school_id`.

### school_onboarding_drafts  (`OnboardingDraftTable`)
KV draft store, unique on `(user_id, step_type, key)`.

### school_classes / school_subjects
Hierarchical: `school_classes` rows reference a school, `school_subjects`
rows reference a class.

### announcements  (`AnnouncementTable`)
One row per announcement. `synced_to_wa` flips to true once it's been queued
to WhatsApp.

### whatsapp_logs  (`WhatsappLogTable`)
One row per (announcement × parent_phone × sync_job). Status field is one of
`QUEUED | SENT | FAILED`. Currently we only ever write `QUEUED` because the
WhatsApp send is a mock.

### admission_enquiries  (`AdmissionEnquiryTable`)
School CRM lead list. `status` ∈ `new | followup | converted`.

### school_philosophy / school_media / storage_metrics
School profile page data. `school_media.kind` is `IMAGE` or `VIDEO`.

### cms_landing_content / app_config
Two KV tables holding JSON blobs that power `/content/landing` and
`/config/app-status`.

### academic_calendar / holiday_list
Drawer-options calendar & holiday list.

### students / faculty / attendance_records
Lookup tables + daily attendance ledger. The unique index
`ux_attendance_unique (school_id, date, type, person_id)` prevents
duplicate same-day entries.

## Seed data

`Seed.populateIfEmpty()` runs at boot. It only writes when the target table is
empty, so it's safe to re-run. See `Seed.kt` for the exact rows; full list is
also summarised in `README.md` §5.

## How to wipe & re-seed (dev only)

```bash
cd /home/user/webapp
rm data.db
./gradlew :server:run
```
SchemaUtils will recreate everything and Seed will refill the demo content.
