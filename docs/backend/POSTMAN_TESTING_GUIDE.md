# Postman Testing Guide — VidyaPrayag Backend

> Branch: **`backend-by-abuzar`** • PR **#1** • Server module: `:server` (Ktor 3.4.3)

This guide walks you through verifying every endpoint introduced in PR #1 using Postman. It assumes you have already installed Postman (desktop or web) and have the repo checked out locally.

## What you will need

| Thing | Why |
|---|---|
| Java 17+ (`java -version`) | To run the Ktor server |
| The repo cloned, branch `backend-by-abuzar` checked out | To start the server |
| Postman (desktop or web) | To send the requests |
| The two files in `docs/backend/postman/` | Collection + environment to import |

> **No external services are needed.** With `DATABASE_URL` unset, the server uses an embedded **SQLite** file at `server/data.db` and the demo data is auto-seeded the first time it boots.

---

## Step 1 — Start the backend server locally

Open a terminal at the repo root and run:

```bash
# 1. Make sure you're on the right branch
git checkout backend-by-abuzar
git pull

# 2. Boot the Ktor server in the foreground
./gradlew :server:run
```

What "working" looks like in the console (first run):

```
> Task :server:run
[main] INFO  com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Start completed.
INFO  Application - Application started in 1.4 seconds.
INFO  Application - Responding at http://0.0.0.0:8080
```

**Sanity check** in a second terminal:

```bash
curl http://localhost:8080/
# → Ktor: Hello, Java! — VidyaPrayag API v1 is live
```

If you see that line, the server is up on port **8080** with the seeded demo data ready.

> **Troubleshooting**
> - `Address already in use` → another process is on 8080. Either stop it or set `PORT=8090 ./gradlew :server:run` and remember to update `{{baseUrl}}` in Postman.
> - `Could not find or load main class` → run `./gradlew :server:build` first to compile.
> - On Windows, replace `./gradlew` with `gradlew.bat`.

---

## Step 2 — Import the Postman collection + environment

Both files live in `docs/backend/postman/`:

- `VidyaPrayag.postman_collection.json` — all 23 endpoints organised in 9 folders
- `VidyaPrayag.local.postman_environment.json` — the `baseUrl` / `token` / `userId` variables

### Import them

1. Open Postman.
2. Click **Import** (top-left).
3. Drag both files in (or use **Upload Files**).
4. In the top-right environment dropdown, pick **VidyaPrayag — Local**.

Postman should now show a collection named **"VidyaPrayag API (backend-by-abuzar)"** in the left sidebar.

> The collection-level auth is set to **Bearer Token = `{{token}}`**, so as soon as you log in (Step 4) every authenticated request picks up your JWT automatically — no copy-paste.

---

## Step 3 — Test the two public endpoints first

These don't need a token and let you confirm the server + seed data are wired correctly.

### 3.1 — Landing CMS

Open **`1. Landing (public) → GET /api/v1/content/landing`** → click **Send**.

✅ Expected (truncated):

```json
{
  "success": true,
  "message": "Landing content fetched",
  "data": {
    "top_tagline": "Education with Trust.",
    "sub_tagline": "Progress with Purpose.",
    "tos_link": "https://vidyaprayag.com/terms",
    "privacy_policy_link": "https://vidyaprayag.com/privacy",
    "login_modes": ["EMAIL","MOBILE","GOOGLE","APPLE"],
    "parent_info":  { ... },
    "school_info":  { ... },
    "list_of_offerings": [ ... ],
    "list_of_portals":   [ ... ]
  }
}
```

If any of the nested objects come back as a raw string, the JSON parsing on the server is off — flag it and we'll investigate.

### 3.2 — App status

Open **`2. App Status (public) → GET /api/v1/config/app-status`** → **Send**.

✅ Expected:

```json
{
  "success": true,
  "data": {
    "version_check": {
      "current_version": "2.4.0",
      "minimum_required_version": "2.3.5",
      "force_update": false,
      "update_url": "https://play.google.com/store/apps/details?id=com.littlebridge.enrollplus",
      "update_message": "A new version with performance improvements is available."
    },
    "maintenance": { "is_under_maintenance": false, ... },
    "flags": { "is_whatsapp_sync_enabled": true, ... }
  }
}
```

> If both of those return `200 OK` with the data above, the database, the seeder, the envelope helper and JSON serialisation are all confirmed working.

---

## Step 4 — Authenticate (the **most important** step)

The next 21 endpoints are all `jwt`-protected. The collection has two paths: signup once (recommended) or use the demo admin via OTP (advanced; see §"Demo data"). The signup flow auto-stores the token for you.

### 4.1 — Check whether the user exists (optional)

**`3. Auth → POST /auth/check-user (brand-new contact)`** → **Send**.

✅ Expected:

```json
{
  "success": true,
  "data": {
    "is_new_user": true,
    "auth_method_required": "PASSWORD",
    "message": "User does not exist. Proceed to signup."
  }
}
```

### 4.2 — Signup (auto-saves the token)

**`3. Auth → POST /auth/signup (email + password — ADMIN)`** → **Send**.

✅ Expected response status **`201 Created`**:

```json
{
  "success": true,
  "message": "Account created successfully",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiJ9...",
    "user_id": "1d8e2a52-...",
    "name": "Postman Tester",
    "role": "ADMIN",
    "profile_completed": false
  }
}
```

After the request finishes, open the environment quick-look (👁 icon top-right) — `token`, `refreshToken`, `userId`, and `role` are now populated. **Every subsequent request now succeeds automatically.**

> If you see `409 Conflict — Account already exists`, the email is already taken from a previous run. Either change the email in the request body, or just run **`POST /auth/login (email + password — re-login)`** which will refresh the token on the same account.

### 4.3 — Verify the token works

**`4. User Details → GET /api/v1/user/details`** → **Send**.

✅ Expected: `200 OK` with onboarding step status (BASIC pending, others LOCKED — because this fresh user hasn't started onboarding yet).

If you get `401 Unauthorized` here, the token was not saved. Re-run the signup or login request and check the test script ran (Postman shows "1 test passed" at the bottom).

---

## Step 5 — Walk the onboarding flow

The dynamic onboarding has 4 steps. Run them **in order** from the **`5. Onboarding`** folder:

| # | Request | What it does |
|---|---|---|
| 1 | `GET /onboarding/step?obStepType=BASIC` | Shows form field schema for BASIC |
| 2 | `POST /onboarding/submit (BASIC)` | Saves draft fields. Should return `"next_step": "BRANDING"` |
| 3 | `GET /onboarding/step?obStepType=BRANDING` | Now `draft_exists=true` only on fields you submitted earlier are echoed back? *(BRANDING fields haven't been submitted yet — they'll all be `draft_exists: false`)* |
| 4 | `POST /onboarding/submit (BRANDING)` | Saves logo + theme color drafts |
| 5 | `GET /onboarding/step?obStepType=ACADEMIC` | Empty `list_of_active_classes` (no classes yet for this user) |
| 6 | `GET /onboarding/academic/class-details?classId=C10` | Will return **`404 — Class not found`** for the fresh account — this is correct. Skip to step 7. |
| 7 | `GET /onboarding/step?obStepType=REVIEW` | Identity + (stub) compliance docs + selected modules |
| 8 | `POST /onboarding/submit (REVIEW — final)` | `is_final_submission: true` → flips `school.onboarding_status = COMPLETED`, returns `is_onboarding_complete: true, redirect_to_home: true` |

> Once step 8 succeeds, **calling `GET /user/details` again** should now show all 4 steps as `COMPLETED` and a `school_id`.

---

## Step 6 — Test the post-onboarding endpoints

After the school exists, the school-scoped endpoints will respond.

### 6.1 — Announcements

| Request | Expected |
|---|---|
| `GET /api/v1/school/announcements` | Empty list (your school has none yet) |
| `GET /api/v1/school/announcements/search?query=summer` | Empty list |
| `POST /api/v1/school/announcements/sync-whatsapp` | `total_queued: 0` (nothing to sync) |

> To see actual data, see **§"Demo data — using the seeded school"** at the end.

### 6.2 — Admissions

| Request | Expected |
|---|---|
| `GET /api/v1/admissions/enquiries/summary` | All counters zero, `recent_enquiries: []`, `efficiency: "0%"` |
| `GET /api/v1/admissions/enquiries?page=1&limit=20` | Empty list with pagination meta |

### 6.3 — User profile

| Request | Expected |
|---|---|
| `GET /api/v1/user/profile` | Default empty profile |
| `PUT /api/v1/user/profile/philosophy` | `200 OK — "Philosophy updated successfully"` |
| `PUT /api/v1/user/profile/tour-videos` | Replaces VIDEO list |
| `PUT /api/v1/user/profile/gallery` | Replaces IMAGE list + returns approximated storage |

After the PUTs, **re-run `GET /api/v1/user/profile`** to confirm the new data is reflected.

### 6.4 — Drawer (School)

| Request | Expected |
|---|---|
| `GET /api/v1/school/analytics` | `isAvailable: false, expectedRelease: "Q3 2024"` (by design, "Coming soon" placeholder) |
| `GET /api/v1/school/calendar?view_type=month&date=2024-05-15` | Empty events + a summary (workdays, holidays) |
| `GET /api/v1/school/holidays?filter_type=yearly` | Empty list (none seeded for *your* school) |
| `GET /api/v1/school/attendance/daily?type=student&grade=Grade 5` | Empty entries, 0 / 0 / 0 |

These are all working but empty — your school has no data yet. To see populated responses, switch to the demo admin (next section).

---

## Demo data — using the seeded school 🎁

The first time the server boots it auto-creates **St. Xavier Academy** with:

- 2 classes (Grade 10 + Grade 5), 5 subjects on Grade 10
- 3 announcements (Holidays, PTM, Events)
- 5 admission enquiries (mix of new / followup / converted)
- 2 calendar events for May 2024
- 4 public yearly holidays
- 6 Grade-5 students with today's attendance, 2 faculty members
- Owner user `demo.admin@vidyaprayag.com` (role `ADMIN`)

That demo admin **has no password set** (it's a fixture). You can still get a token for it via the **phone OTP path on a fresh account that we then attach manually**, but the cleanest dev-only trick is:

### Option A — Add a password to the demo admin (one SQL line)

The SQLite DB is at `server/data.db`. With sqlite3 installed:

```bash
sqlite3 server/data.db
sqlite> .headers on
sqlite> SELECT id, name, contact, role FROM users WHERE contact = 'demo.admin@vidyaprayag.com';
-- → grab the id
sqlite> UPDATE users
        SET password_hash = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92'
        WHERE contact = 'demo.admin@vidyaprayag.com';
sqlite> .quit
```

That hash is SHA-256 of the literal string `"123456"`. Now in Postman:

```json
POST /api/v1/auth/login
{
  "identifier": "demo.admin@vidyaprayag.com",
  "password": "123456",
  "role": "ADMIN"
}
```

The login response will overwrite `{{token}}` with the demo admin's token, and every subsequent request will read the **St. Xavier Academy** dataset. Now:

- `GET /api/v1/school/announcements` → 3 items
- `GET /api/v1/admissions/enquiries/summary` → counts 5 enquiries, efficiency string
- `GET /api/v1/school/calendar?view_type=month&date=2024-05-15` → May Day + Unit Test 1
- `GET /api/v1/school/holidays?filter_type=yearly` → 4 holidays
- `GET /api/v1/school/attendance/daily?type=student&grade=Grade 5` → 6 students, 5 present, 1 absent
- `GET /api/v1/onboarding/academic/class-details?classId=C10` → 5 subjects

### Option B — Keep using your fresh signup user

Stick with the user you created in Step 4. Manually create classes, subjects, announcements and enquiries by hitting the API. This mirrors what the mobile app will actually do.

---

## Reading the response envelope

Every JSON response uses this consistent shape (defined in `core/ApiResponse.kt`):

```json
{
  "success": true,
  "message": "Some human-readable message",
  "data": { ... }            // payload-specific
}
```

On errors:

```json
{
  "success": false,
  "message": "Description of the failure",
  "data": null
}
```

…with appropriate HTTP status code (`400`, `401`, `404`, `409`, `500`, …).

---

## Cheat sheet — base URLs by environment

| Env | `baseUrl` | Notes |
|---|---|---|
| Local dev | `http://localhost:8080` | default in the imported environment |
| Local non-default port | `http://localhost:<PORT>` | when you ran `PORT=8090 ./gradlew :server:run` |
| Android emulator hitting host | `http://10.0.2.2:8080` | only relevant when testing from the Android app, not Postman |
| Staging / prod | whatever the operator deploys | switch the value in the **Local** environment, or duplicate the environment |

---

## Known mocks / placeholders to be aware of

| Endpoint | Mock |
|---|---|
| `POST /auth/send-otp` | Always returns success, OTP is hard-coded `123456` |
| `POST /auth/signup` (phone path) | Accepts `otp: "123456"` only |
| `POST /school/announcements/sync-whatsapp` | Queues rows in `whatsapp_logs` table but does **not** call WhatsApp Cloud API |
| `GET /school/analytics` | "Coming soon" placeholder by design |
| `PUT /user/profile/gallery` | Storage usage is approximated `0.2 GB × imageCount` |

All of these are documented in `docs/backend/MANUAL_STEPS.md` with the work needed to make them production-grade.

---

## Reporting issues back

If a request behaves unexpectedly:

1. Note the **request name** (folder + name from the collection).
2. Copy the **full response body** + the **status code**.
3. Note the server console output (the line starting with `[main] INFO ...`).
4. Drop them in the PR thread for #1, or open a follow-up issue.

The server logs every request (`CallLogging` plugin) so the matching server-side line is usually right there.

---

## Where the files live

```
docs/backend/
├── POSTMAN_TESTING_GUIDE.md            ← you are here
└── postman/
    ├── VidyaPrayag.postman_collection.json
    └── VidyaPrayag.local.postman_environment.json
```

Happy testing!
