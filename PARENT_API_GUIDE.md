# VidyaPrayag — Parent Ecosystem API Guide

> Branch: **`backend-by-abuzar`**
> Spec: **`parent_api_spec.artifact.md`**
> What's new: 7 endpoints (6 JWT-protected, 1 public) under `/api/v1/parent/*` and `/api/v1/content/support`.

This guide shows you, end-to-end, how to:

1. Set up your `.env` file (which variables, where to get them).
2. Run the Ktor backend on Windows / macOS / Linux from CMD/terminal.
3. Test every new parent endpoint in Postman.

> The "build inside this sandbox" path is impractical (Gradle + Kotlin Multiplatform downloads multiple GB on a cold clone). Run it on **your own machine** — the build is fast once you pass `-Pserver-only=true`.

---

## 1. Prerequisites

| Tool         | Version | How to check        |
|--------------|---------|---------------------|
| **JDK**      | 21+     | `java -version`     |
| **Git**      | any     | `git --version`     |
| **Postman**  | desktop | (no CLI needed)     |

Install JDK 21 from <https://adoptium.net/temurin/releases/?version=21> if `java -version` prints 17 or older.

You do **not** need to install Gradle separately — the repo ships `gradlew.bat` (Windows) / `./gradlew` (mac/linux).

---

## 2. Pull the branch

```cmd
:: Windows CMD
git clone https://github.com/hereugo-ak/VidyaPrayag.git
cd VidyaPrayag
git checkout backend-by-abuzar
git pull origin backend-by-abuzar
```

```bash
# macOS / Linux
git clone https://github.com/hereugo-ak/VidyaPrayag.git
cd VidyaPrayag
git checkout backend-by-abuzar
git pull origin backend-by-abuzar
```

---

## 3. Create your local `.env` file

In the repo root, copy `.env.example` to `.env`:

```cmd
copy .env.example .env
```

```bash
cp .env.example .env
```

### 3.1 Minimum variables to run locally (SQLite mode)

Open `.env` and set **only** these. Everything else can stay blank:

| Variable               | Set it to              | Why |
|------------------------|------------------------|-----|
| `DATABASE_URL`         | _(leave empty)_        | Empty → SQLite fallback (`./data.db`) auto-created on first boot. Zero setup. |
| `PORT`                 | `8080`                 | The port Ktor listens on. |
| `JWT_SECRET`           | any long random string | Used to sign JWTs. Generate with `openssl rand -hex 64`. Pick anything for dev. |
| `OTP_PEPPER`           | any long random string | Used to hash OTPs. `openssl rand -hex 32`. Pick anything for dev. |
| `OTP_DEV_RETURN_CODE`  | `true`                 | Makes `/send-otp` echo the OTP in the response. **DO NOT use in production**. |
| `APP_SEED_CMS`         | `true`                 | First-boot inserts the parent CMS keys (screen titles, badges, focus areas, …) so the dashboard returns rich data immediately. |

> Want to point at Supabase Postgres instead? Set `DATABASE_URL` to your Supabase pooler URI **and** set `AUTO_CREATE_TABLES=true` once (then turn it off). All other variables stay the same.

### 3.2 Optional: full OTP delivery (Fast2SMS / MSG91 / Twilio / SMTP)

For the parent endpoints **you don't need any OTP provider** — you just need a valid JWT. The simplest path is:

- `OTP_DEV_RETURN_CODE=true`  → Login returns the OTP code in the JSON response, no SMS needed.

If you _do_ want to test real SMS, the `.env.example` file has a per-provider section. Get credentials here:

| Provider     | Where to get keys                                      |
|--------------|--------------------------------------------------------|
| Fast2SMS     | <https://www.fast2sms.com> → Dashboard → Developer API |
| MSG91        | <https://msg91.com> → Auth Key                         |
| Twilio       | <https://console.twilio.com> → Account → API keys      |
| SMTP (email) | Gmail App Password, Resend, SES, Mailgun, …            |

Leave them all blank if you just want to test with `dev_otp`.

### 3.3 Anything you must NEVER commit

`.gitignore` already ignores `.env`. Don't commit it. Never paste secrets into PRs.

---

## 4. Run the server (fast path)

```cmd
:: Windows
gradlew.bat :server:run -Pserver-only=true
```

```bash
# macOS / Linux
./gradlew :server:run -Pserver-only=true
```

The `-Pserver-only=true` flag skips the `:composeApp` + `:shared` Kotlin Multiplatform modules — without it, a cold first build downloads ~3 GB and takes 30+ minutes. With it, the cold build is ~5 minutes and subsequent runs ~10 seconds.

You'll see something like:

```
DB_INIT: isPostgres=false, AUTO_CREATE_TABLES='null' -> false
DB_INIT: Running SchemaUtils.createMissingTablesAndColumns for 23 tables...
DB_INIT: Schema check/creation completed.
DB_INIT: Running CMS seed...
DB_INIT: CMS seed completed successfully.
... INFO  ktor.application - Application started in 1.234 seconds.
... INFO  ktor.application - Responding at http://0.0.0.0:8080
```

Leave the terminal open. Press **Ctrl+C** to stop.

---

## 5. Smoke-test the server

In a second terminal:

```cmd
curl http://127.0.0.1:8080/
```

Expected: `Ktor: Hello, JVM — VidyaPrayag API v1 is live`

---

## 6. Import Postman collection + environment

1. Open Postman desktop.
2. Click **Import** (top-left).
3. Drag-and-drop these **two** files into the import dialog:
   - `docs/backend/postman/VidyaPrayag.postman_collection.json`
   - `docs/backend/postman/VidyaPrayag.local.postman_environment.json`
4. Select environment **VidyaPrayag — Local** in the top-right dropdown.

You should now see folder **10. Parent Ecosystem (parent_api_spec)** in the collection — that's what you'll test.

---

## 7. Get a JWT (Login)

The parent endpoints (except `/content/support`) require `Authorization: Bearer <jwt>`. Get one via OTP:

### 7.1 Send OTP

Postman → **3. Auth (public) → Send OTP**, body:

```json
{ "identifier": "{{phone_number}}", "purpose": "login" }
```

Response (because `OTP_DEV_RETURN_CODE=true`):

```json
{
  "success": true,
  "data": { "dev_code": "123456", "expires_at": "…" }
}
```

The test script auto-stores `dev_code` into the `{{dev_otp}}` env var.

### 7.2 Sign up (first time only) **OR** Login (returning user)

Use **Signup** for a new number, **Login** for an existing one. Both have Tests scripts that auto-save `token` + `user_id` into the environment.

After Login you should see in the Postman console:

```
[VP] token stored: eyJhbGciOi…
```

---

## 8. Test the parent endpoints (folder **10. Parent Ecosystem**)

Run them in this order. All inherit the `Authorization: Bearer {{token}}` header from the collection.

| # | Request                                       | What it tests |
|---|-----------------------------------------------|---------------|
| 1 | GET `/content/support`                        | Public — drawer support config, no JWT needed. |
| 2 | GET `/parent/onboarding/metadata`             | Step 1 dropdowns + header copy (CMS-driven). |
| 3 | POST `/parent/onboarding/child-info`          | Creates a child for the JWT user. Stores `{{child_id}}` automatically. |
| 4 | GET `/parent/onboarding/preference-options`   | Step 2 filters: boards, focus areas, budget. |
| 5 | GET `/parent/dashboard`                       | Home screen. `child_summary` will now be your Aarav from step 3. |
| 6 | GET `/parent/track-progress`                  | Holistic Growth view — hero/badges/EI/play. |
| 7 | GET `/parent/fees`                            | Fees stats + announcements. Empty until you insert `fee_records`. |

### 8.1 Expected output of #5 after running #3 first

```json
{
  "success": true,
  "message": "Dashboard fetched successfully",
  "data": {
    "greeting": "Good Morning, …",
    "child_summary": {
      "id": "uuid",
      "name": "Aarav Sharma",
      "overall_progress": 0.0,
      "current_level": 1,
      "attendance_status": "PRESENT",
      "profile_pic": null
    },
    "alerts": [
      { "id": "ptm_upcoming", "title": "Upcoming PTM", "value": "Nov 25", "type": "INFO" }
    ],
    "featured_schools": [],
    "curation_logic": "Curation aligned with NEP 2020 developmental milestones."
  }
}
```

`featured_schools` is empty until a school is onboarded (run the school onboarding flow from folder **5** or insert one directly into the DB).

### 8.2 Adding fake fee data to test `/parent/fees`

If you want non-empty fees in dev:

```sql
-- run against ./data.db (SQLite) or your Supabase Postgres
INSERT INTO fee_records (id, parent_id, title, amount, currency, status, category, created_at, updated_at)
VALUES
  (lower(hex(randomblob(16))), '<YOUR-USER-ID>', 'Q1 Tuition', 12000, 'INR', 'PAID',    'Tuition',   datetime('now'), datetime('now')),
  (lower(hex(randomblob(16))), '<YOUR-USER-ID>', 'Q2 Tuition',  8000, 'INR', 'DUE',     'Tuition',   datetime('now'), datetime('now')),
  (lower(hex(randomblob(16))), '<YOUR-USER-ID>', 'Late fee',     500, 'INR', 'OVERDUE', 'Penalty',   datetime('now'), datetime('now'));
```

Replace `<YOUR-USER-ID>` with the value of `{{user_id}}` Postman saved during login. The `/parent/fees` response will then return:

```json
{
  "stats": {
    "total_collected": "₹12,000",
    "progress": 0.585…,
    "outstanding": "₹8,500",
    "overdue_count": 1
  },
  "announcements": [...]
}
```

### 8.3 Customising CMS strings (no redeploy)

All parent UI copy is in `app_config`. Examples:

```sql
-- Change the dashboard curation line
UPDATE app_config SET value = '"Curation aligned with NCF 2023 milestones."'
WHERE key = 'parent_dashboard_curation_logic';

-- Add a new badge
UPDATE app_config SET value = '[
  {"title":"Social Star","icon":"workspace_premium","is_locked":false,"colors":["#B6C7EB","#006C49"]},
  {"title":"Math Whiz","icon":"calculate","is_locked":false,"colors":["#FFD580","#B26A00"]},
  {"title":"Storyteller","icon":"menu_book","is_locked":true,"colors":["#E0BBE4","#957DAD"]}
]' WHERE key = 'parent_track_badges';
```

The next `/parent/track-progress` call returns the new content immediately.

---

## 9. Troubleshooting

| Symptom                                       | Cause                                          | Fix |
|-----------------------------------------------|------------------------------------------------|-----|
| `401 UNAUTHORIZED` on every parent call       | `{{token}}` env var is empty                   | Re-run **Login**; check its Tests tab actually stored the token |
| `Port 8080 already in use`                    | Another process bound                          | `set PORT=8088` (CMD) then re-run gradle, and change `baseUrl` in Postman to `http://localhost:8088` |
| Build runs 30+ minutes                        | You forgot `-Pserver-only=true`                | Ctrl+C → `gradlew.bat --stop` → re-run with flag |
| `child_summary: null` after creating a child  | Different JWT user (logged in twice)           | Make sure the same `{{token}}` is used for both POST `/child-info` and GET `/dashboard` |
| `/parent/fees` returns all zeros              | No `fee_records` for this parent yet           | Insert rows as shown in §8.2 |
| `relation "children" does not exist` (PG)     | Forgot to set `AUTO_CREATE_TABLES=true` once   | Set it, restart the server once, then turn it off |

---

## 10. What changed on this branch (for code reviewers)

- **5 new Kotlin files** under `server/src/main/kotlin/com.littlebridge.enrollplus/feature/`:
  - `parent/ParentOnboardingRouting.kt`
  - `parent/ParentDashboardRouting.kt`
  - `parent/TrackProgressRouting.kt`
  - `parent/ParentFeesRouting.kt`
  - `content/SupportRouting.kt`
- **2 new Exposed tables** in `db/Tables.kt`: `ChildrenTable`, `FeeRecordsTable` (added to `DatabaseFactory.allTables`).
- **17 new CMS keys** seeded by `CmsSeed.ensureLandingAndConfig()` in `db/Seed.kt` (all `parent_*` and `parent_support_config`).
- **Application.kt** wires the 5 new routings.
- **Postman collection** gets a new folder *10. Parent Ecosystem* with 7 ready-to-fire requests.
- **`docs/backend/12-parent-ecosystem.md`** documents every endpoint, response shape, and aggregation rule.

No existing endpoints, tables, or behaviours were changed.
