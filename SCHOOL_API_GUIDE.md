# VidyaPrayag — School Ecosystem API Guide

> Branch: **`backend-by-abuzar`**
> Spec: **`school_api_spec.artifact.md`**
> What's new: **16 JWT-protected endpoints** under `/api/v1/school/*` covering School Dashboard, Analytics, Leave Requests, PTM, Messages, and Results.

This guide shows you, end-to-end, how to:

1. Set up your `.env` file (which variables, where to get them).
2. Run the Ktor backend on Windows / macOS / Linux from CMD/terminal.
3. Test every new school endpoint in Postman (and curl).

> Same pattern as `PARENT_API_GUIDE.md`. If you've already followed that guide you can skip §1–§6 and jump to §7 (JWT login) and §8 (the new school folder).

---

## 1. Prerequisites

| Tool         | Version | How to check        |
|--------------|---------|---------------------|
| **JDK**      | 21+     | `java -version`     |
| **Git**      | any     | `git --version`     |
| **Postman**  | desktop | (no CLI needed)     |
| **curl**     | any     | `curl --version`    |

Install JDK 21 from <https://adoptium.net/temurin/releases/?version=21> if `java -version` prints 17 or older. The repo ships `gradlew.bat` (Windows) / `./gradlew` (mac/linux), so you do **not** need Gradle installed separately.

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
| `APP_SEED_CMS`         | `true`                 | First-boot inserts the **12 school CMS keys** (dashboard / analytics / results / etc.) so every endpoint returns rich data immediately. |

> Want to point at Supabase Postgres instead? Set `DATABASE_URL` to your Supabase pooler URI **and** set `AUTO_CREATE_TABLES=true` once (then turn it off). All other variables stay the same.

### 3.2 Anything you must NEVER commit

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
DB_INIT: Running SchemaUtils.createMissingTablesAndColumns for 29 tables...
DB_INIT: Schema check/creation completed.
DB_INIT: Running CMS seed...
DB_INIT: CMS seed completed successfully.
... INFO  ktor.application - Application started in 1.234 seconds.
... INFO  ktor.application - Responding at http://0.0.0.0:8080
```

Leave the terminal open. Press **Ctrl+C** to stop.

The table count is **29** on this branch (up from 23 in the parent guide) because we added 6 new tables: `leave_requests`, `ptm_events`, `ptm_class_progress`, `message_threads`, `messages`, `exam_results`.

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

You should now see folder **11. School Ecosystem (school_api_spec)** in the collection — that's what you'll test in §8.

---

## 7. Get a JWT (Login)

Every school endpoint requires `Authorization: Bearer <jwt>`. Get one via OTP:

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

Use **Signup** for a new phone number, **Login** for an existing one. Both have Tests scripts that auto-save `token` + `user_id` + `role` into the environment.

After Login you should see in the Postman console:

```
[VP] token stored: eyJhbGciOi…
[VP] role stored: admin
```

> **The school endpoints assume the JWT belongs to an admin/staff user with an `school_id` set on `app_users`.**  If you signed up as a parent, do this once to "promote" yourself to admin of a school (SQLite shown — Postgres is identical):
>
> ```sql
> -- 1) create a school (if none exists)
> INSERT INTO schools (id, name, slug, board, medium, city, district, state, created_at, updated_at)
> VALUES (lower(hex(randomblob(16))), 'Demo School', 'demo-school', 'CBSE', 'English',
>         'Lucknow', 'Lucknow', 'Uttar Pradesh', datetime('now'), datetime('now'));
>
> -- 2) attach it to your account (use {{user_id}} from Postman)
> UPDATE app_users
>    SET role = 'admin',
>        school_id = (SELECT id FROM schools WHERE slug = 'demo-school')
>  WHERE id = '<YOUR-USER-ID>';
> ```
>
> Then re-run **Login** so the new role + school_id are baked into a fresh JWT.

---

## 8. Test the school endpoints (folder **11. School Ecosystem**)

Run them in this order. All inherit `Authorization: Bearer {{token}}` from the collection root.

| #  | Request                                                | What it tests |
|----|--------------------------------------------------------|---------------|
| 1  | GET `/school/dashboard`                                | Home — welcome card, 4-step onboarding (live join against `school_onboarding_drafts`), support card. |
| 2  | GET `/school/analytics/overview`                       | Performance trend + 4 KPI cards (card[0].value patched with live avg attendance %) + insights. |
| 3  | GET `/school/analytics/class-performance?class=Grade 10-A` | Class blob with `summary.active_students` overlaid by live `students` count. |
| 4  | GET `/school/analytics/teacher-performance`            | Teacher blob with `star_faculty` overlaid by top-3 active `faculty` rows. |
| 5  | GET `/school/analytics/student/{{student_code}}`       | Per-student detail; `student_code` defaults to `ED-001` in the env. KPI attendance is live (30d). |
| 6  | GET `/school/analytics/syllabus-coverage`              | Pure CMS — verbatim `school_syllabus_coverage`. |
| 7  | GET `/school/leave-requests?type=student&status=Pending` | Tab list + headline `approval_rate` (30d) + `weekly_count` (7d). |
| 8  | POST `/school/leave-requests`                          | Creates a pending request. Auto-saves `{{leave_request_id}}`. |
| 9  | PATCH `/school/leave-requests/{{leave_request_id}}/status` | Flips status to `Approved`; stamps `actioned_by` + `actioned_at`. |
| 10 | GET `/school/ptm`                                      | `active_event` + history + per-class progress. |
| 11 | POST `/school/ptm`                                     | Schedule new PTM; auto-saves `{{ptm_event_id}}`. |
| 12 | GET `/school/messages/threads`                         | Owner-scoped inbox; server-formatted `time`. |
| 12b | GET `/school/messages/threads/{{thread_id}}/messages` | **Conversation view** — all messages in a thread (oldest→newest); opening it clears the unread badge. Returns `{ thread_id, sender_name, messages:[{id,body,is_mine,created_at,time}] }`. |
| 13 | POST `/school/messages` (thread_id=null)               | Creates a brand-new thread; auto-saves `{{thread_id}}`. |
| 14 | POST `/school/messages/threads/{{thread_id}}/read`     | Marks the new thread read. |
| 15 | GET `/school/results?test=Unit Test II&class=Grade 10-A&subject=Mathematics` | CMS filter lists + live aggregates. Initially empty `students` array. |
| 16 | POST `/school/results`                                 | Publish 2 sample results (bulk upsert); response `{ upserted: 2 }`. Re-run #15 to see them. |

### 8.1 Expected output of `#1 GET /school/dashboard`

```json
{
  "success": true,
  "message": "School dashboard fetched successfully",
  "data": {
    "welcome": {
      "title": "Welcome, Admin",
      "subtitle": "Your institutional setup is ready to begin. Let's build your digital campus.",
      "cta_label": "Start Onboarding"
    },
    "onboarding_progress": {
      "completed_steps": 0,
      "total_steps": 4,
      "progress": 0.0,
      "status_label": "Pending",
      "steps": [
        { "id": 1, "title": "Institutional Basics", "description": "School name, location, and IDs.", "is_completed": false, "icon_url": "https://…" },
        { "id": 2, "title": "Branding & Identity",  "description": "Upload logos and color themes.",   "is_completed": false, "icon_url": "https://…" },
        { "id": 3, "title": "Academic Setup",        "description": "Classes, subjects, and teachers.","is_completed": false, "icon_url": "https://…" },
        { "id": 4, "title": "Final Launch",          "description": "Verify and go live.",             "is_completed": false, "icon_url": null }
      ]
    },
    "support": {
      "title": "Need help? Chat with an expert",
      "subtitle": "Available 24/7 for institutions",
      "action_label": "CHAT",
      "video_label": "Watch Onboarding Video",
      "video_url": "https://vidyaprayag.com/onboarding/intro.mp4"
    }
  }
}
```

If you actually run the **School Onboarding** flow first (folder 5 → Submit Basic step), `completed_steps` jumps to 1 and `status_label` flips to `"In Progress"`.

### 8.2 Chaining the leave-request flow (#7 → #8 → #9)

The test script attached to `POST /school/leave-requests` auto-saves the returned `id` into `{{leave_request_id}}`. So you can:

1. Run **#7** — observe an empty `requests: []` (first time).
2. Run **#8** — body is pre-filled with Marcus Holloway's family-function request.  Postman console prints `[VP] leave_request_id stored: 8a4e…`.
3. Run **#7 again** — the new row is in the list with `status: "Pending"`, `date_range: "Dec 12 - Dec 14"`.
4. Run **#9** — flips status to Approved. Re-run **#7** → now `approval_rate: 100`, `weekly_count: 1`.

### 8.3 Chaining the PTM flow (#10 → #11 → #10)

1. **#10** — initial response has `active_event: null`, `history: []`.
2. **#11** — schedules Mid-Year PTM on `2026-01-15`. Postman saves `{{ptm_event_id}}`.
3. **#10** again — `active_event` is now your Mid-Year PTM (because its date is in the future).

To see `class_progress` populated, insert a couple rows manually (the spec doesn't ship a writer for `ptm_class_progress` yet — class teams update it as meetings happen):

```sql
INSERT INTO ptm_class_progress (id, ptm_event_id, class_name, teacher_name, met_count, total_count, updated_at)
VALUES (lower(hex(randomblob(16))), '<{{ptm_event_id}}>', '10A', 'Ms. Sarah Jenkins', 28, 30, datetime('now')),
       (lower(hex(randomblob(16))), '<{{ptm_event_id}}>', '10B', 'Mr. Adrian Chen',   12, 28, datetime('now'));
```

Re-run **#10** → `class_progress` now has 2 rows, each with `progress = met/total`.

### 8.4 Chaining the messages flow (#12 → #13 → #14 → #12)

1. **#12** — empty `threads: []`.
2. **#13** — sends a system message to yourself (`recipient_user_id: null` defaults to the JWT user). Postman saves `{{thread_id}}`.
3. **#12** again — your new thread shows with `unread_count: 0`, `is_read: true` (sender = recipient).
4. **#14** — explicit "mark read" (no-op in this self-send case, but exercises the endpoint).

### 8.5 Chaining the results flow (#15 → #16 → #15)

1. **#15** — empty `students`, `class_average: "0.0"`, all counts 0.
2. **#16** — bulk-publishes Marcus (98 / Exceeding) + Sarah (72 / Meeting). Response: `{ "upserted": 2 }`.
3. **#15** again — `class_average: "85.0"`, `exceeding_count: 1`, `meeting_count: 1`, `students` has 2 rows.
4. **#16** again with a higher score — re-running the same publish UPDATES rather than inserts (the unique tuple `(school, test, class, subject, student_id)` enforces idempotency).

### 8.6 Same flow with `curl` (no Postman)

If you'd rather drive the API from a terminal, here's the abbreviated set. Replace `$TOKEN` with the JWT you got from `/api/v1/auth/login`.

```bash
BASE=http://127.0.0.1:8080
TOKEN=eyJhbGciOi…   # paste yours

# 1) Dashboard
curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/v1/school/dashboard | jq

# 2) Analytics overview
curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/v1/school/analytics/overview | jq

# 3) Class performance
curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/school/analytics/class-performance?class=Grade%2010-A" | jq

# 4) Teacher performance
curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/v1/school/analytics/teacher-performance | jq

# 5) Student analytics (UUID or student_code)
curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/v1/school/analytics/student/ED-001 | jq

# 6) Syllabus coverage
curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/v1/school/analytics/syllabus-coverage | jq

# 7) Leave requests — list
curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/school/leave-requests?type=student&status=Pending" | jq

# 8) Leave requests — create
LEAVE_ID=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"requester_name":"Marcus Holloway","requester_role":"student","date_from":"2025-12-12","date_to":"2025-12-14","reason":"Family Function"}' \
  $BASE/api/v1/school/leave-requests | jq -r '.data.id')
echo "Created leave request: $LEAVE_ID"

# 9) Leave requests — patch status
curl -s -X PATCH -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"status":"Approved"}' \
  $BASE/api/v1/school/leave-requests/$LEAVE_ID/status | jq

# 10) PTM — list
curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/v1/school/ptm | jq

# 11) PTM — schedule
PTM_ID=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"Mid-Year PTM","date":"2026-01-15","slot":"10:00 - 14:00","expected_parents":600}' \
  $BASE/api/v1/school/ptm | jq -r '.data.id')
echo "Created PTM: $PTM_ID"

# 12) Messages — list threads
curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/v1/school/messages/threads | jq

# 13) Messages — send (creates thread)
THREAD_ID=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"thread_id":null,"sender_name":"Admin Desk","sender_role":"Support","body":"Welcome!"}' \
  $BASE/api/v1/school/messages | jq -r '.data.thread_id')
echo "Created thread: $THREAD_ID"

# 12b) Messages — open conversation (this is the endpoint the app modal hits)
curl -s -H "Authorization: Bearer $TOKEN" \
  $BASE/api/v1/school/messages/threads/$THREAD_ID/messages | jq

# 14) Messages — mark read
curl -s -X POST -H "Authorization: Bearer $TOKEN" $BASE/api/v1/school/messages/threads/$THREAD_ID/read | jq

# 15) Results — list
curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/school/results?test=Unit%20Test%20II&class=Grade%2010-A&subject=Mathematics" | jq

# 16) Results — publish
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{
  "test":"Unit Test II",
  "class":"Grade 10-A",
  "subject":"Mathematics",
  "results":[
    {"student_id":"ED-001","student_name":"Marcus Holloway","score":"98","attendance":"100%","status":"Exceeding","trend":"+2.4%"},
    {"student_id":"ED-002","student_name":"Sarah Miller",   "score":"72","attendance":"88%", "status":"Meeting",  "trend":"+0.5%"}
  ]
}' $BASE/api/v1/school/results | jq
```

### 8.7 Customising CMS strings (no redeploy)

All school UI copy and reference lists live in `app_config`. Examples:

```sql
-- Change the dashboard welcome banner
UPDATE app_config SET value = '{"title":"Namaste, Principal","subtitle":"Welcome back to your campus.","cta_label":"Continue"}'
WHERE key = 'school_dashboard_welcome';

-- Add a new subject to the Results filter dropdown
UPDATE app_config SET value = '{
  "available_tests":["Unit Test I","Unit Test II","Mid Term","Final"],
  "available_classes":["Grade 10-A","Grade 10-B","Grade 11-A","Grade 11-B"],
  "available_subjects":["Mathematics","Science","Literature","History","Chemistry","Physics","Computer Science"]
}' WHERE key = 'school_results_filters';
```

The next `/school/dashboard` and `/school/results` calls return the new content immediately.

---

## 9. Troubleshooting

| Symptom                                                | Cause                                            | Fix |
|--------------------------------------------------------|--------------------------------------------------|-----|
| `401 UNAUTHORIZED` on every school call                | `{{token}}` env var is empty                     | Re-run **Login**; check its Tests tab actually stored the token |
| `404 User not associated with any school`              | JWT user has no `school_id` on `app_users`       | Run the SQL in §7.2 to attach a school to your user, then re-login |
| `404 Student not found` on `/analytics/student/{id}`   | `student_code` doesn't exist in `students`       | Insert a row: `INSERT INTO students (id, school_id, student_code, full_name, class_name, section, roll_number, is_active, created_at) VALUES (lower(hex(randomblob(16))), '<your-school-id>', 'ED-001', 'Marcus Holloway', 'Grade 10-A', 'A', '12', 1, datetime('now'));` |
| Empty `cards[0].value` is always the CMS default 94%   | No `attendance_records` for this school yet      | Live overlay only kicks in once there's at least one row; the CMS default is the safe fallback |
| `Port 8080 already in use`                             | Another process bound                            | `set PORT=8088` (CMD) then re-run gradle, and change `baseUrl` in Postman to `http://localhost:8088` |
| Build runs 30+ minutes                                 | You forgot `-Pserver-only=true`                  | Ctrl+C → `gradlew.bat --stop` → re-run with flag |
| `relation "leave_requests" does not exist` (PG)        | Forgot to set `AUTO_CREATE_TABLES=true` once     | Set it, restart the server once, then turn it off |
| `POST /school/results` returns `400 results must not be empty` | `results` array is empty in the body     | Send at least one row in `results[]` |

---

## 10. What changed on this branch (for code reviewers)

- **6 new Kotlin files** under `server/src/main/kotlin/com.littlebridge.enrollplus/feature/school/`:
  - `SchoolDashboardRouting.kt`
  - `SchoolAnalyticsRouting.kt`
  - `LeaveRequestsRouting.kt`
  - `PtmRouting.kt`
  - `MessagesRouting.kt`
  - `ResultsRouting.kt`
- **6 new Exposed tables** in `db/Tables.kt`: `LeaveRequestsTable`, `PtmEventsTable`, `PtmClassProgressTable`, `MessageThreadsTable`, `MessagesTable`, `ExamResultsTable` (all added to `DatabaseFactory.allTables`).
- **12 new CMS keys** seeded in `db/Seed.kt` (`school_dashboard_*`, `school_analytics_*`, `school_class_performance`, `school_teacher_performance`, `school_student_analytics_*`, `school_syllabus_coverage`, `school_results_filters`).
- **`Application.kt`** imports and mounts the 6 new routings inside the authenticated routing block.
- **Postman collection** gets a new folder **"11. School Ecosystem"** with 16 ready-to-fire requests (including chained test scripts).
- **Postman env** gains `leave_request_id`, `ptm_event_id`, `thread_id`, `student_code`.
- **`docs/backend/13-school-ecosystem.md`** documents every endpoint, response shape, compute rule, error code, and CMS key.
- **`school_api_spec.artifact.md`** is the formal contract (root-level, mirroring `parent_api_spec.artifact.md`).

No existing endpoints, tables, or behaviours were changed. Parent-side endpoints from `f34a095` are untouched.

---

## 11. Design guarantees (the "no hardcoding" contract)

1. Every UI string, badge list, chart series, and filter dropdown is **CMS-driven** (`app_config` key seeded idempotently).
2. Every live aggregate (attendance %, approval rate, top-3 faculty, class average, …) is computed against the **real table** on each request.
3. Every response goes through the canonical envelope `{ success, message, data }` via `core/ResponseExtensions.kt`.
4. Every endpoint sits inside `authenticate("jwt") { … }`. Identity is sourced from `principalUserId()` / `principalRole()` / `principalName()`.
5. CMS seeds are **insert-if-missing only** — operator edits in Supabase survive backend redeploys.
6. The Results POST is a **portable bulk upsert** against the unique tuple `(school, test, class, subject, student_id)` — calling it twice updates rather than duplicates.
7. Messages inboxes are **owner-scoped** to `JWT.sub` — admins never see each other's threads.
