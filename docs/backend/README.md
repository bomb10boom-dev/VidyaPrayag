# VidyaPrayag — Backend Documentation (branch `backend-by-abuzar`)

This folder documents every backend file added in the `backend-by-abuzar`
branch. Each markdown file maps 1-to-1 to a Kotlin source file under
`server/src/main/kotlin/com.littlebridge.enrollplus/`.

> Scope: only the endpoints listed in the two API spec artifacts
> (`vidya_prayag_api_spec.artifact.md` and `vidya_prayag_api_spec2.artifact.md`)
> have been implemented. The wider VidyaPrayag plan (parents portal, AI
> narratives, payments, etc.) is intentionally **out of scope** until the UI
> for those screens exists.

---

## 1. High-level Architecture

```
server/src/main/kotlin/com.littlebridge.enrollplus/
├── Application.kt                       ← Ktor bootstrap, plugin install, route mount
├── core/
│   ├── ApiResponse.kt                   ← {success,message,data} envelope DTO
│   ├── ResponseExtensions.kt            ← call.ok/created/accepted/fail helpers
│   ├── JwtConfig.kt                     ← HMAC256 issue+verify (env-driven)
│   ├── SecurityModule.kt                ← Ktor JWT auth provider
│   └── ErrorHandling.kt                 ← StatusPages → uniform error JSON
├── db/
│   ├── DatabaseFactory.kt               ← Hikari + Exposed init (Postgres|SQLite)
│   ├── UserTable.kt                     ← users (existing + new columns)
│   ├── Tables.kt                        ← all other Exposed tables
│   └── Seed.kt                          ← idempotent first-run seeder
└── feature/
    ├── content/LandingRouting.kt        → /api/v1/content/landing
    ├── content/SupportRouting.kt        → /api/v1/content/support
    ├── config/AppStatusRouting.kt       → /api/v1/config/app-status
    ├── auth/AuthRouting.kt              → /api/v1/auth/* (+ legacy /auth/*)
    ├── user/
    │   ├── UserDetailsRouting.kt        → /api/v1/user/details
    │   └── UserProfileRouting.kt        → /api/v1/user/profile[…]
    ├── onboarding/OnboardingRouting.kt  → /api/v1/onboarding/*
    ├── announcements/AnnouncementRouting.kt → /api/v1/school/announcements*
    ├── admissions/AdmissionRouting.kt   → /api/v1/admissions/enquiries[…]
    ├── school/                          ← parent_api_spec.artifact.md + school_api_spec.artifact.md
    │   ├── SchoolRouting.kt             → /api/v1/school/{analytics,calendar,holidays,attendance/daily}
    │   ├── SchoolDashboardRouting.kt    → /api/v1/school/dashboard
    │   ├── SchoolAnalyticsRouting.kt    → /api/v1/school/analytics/{overview,class-performance,teacher-performance,student/{id},syllabus-coverage}
    │   ├── LeaveRequestsRouting.kt      → /api/v1/school/leave-requests[…]
    │   ├── PtmRouting.kt                → /api/v1/school/ptm
    │   ├── MessagesRouting.kt           → /api/v1/school/messages[…]
    │   └── ResultsRouting.kt            → /api/v1/school/results
    └── parent/                          ← parent_api_spec.artifact.md
        ├── ParentOnboardingRouting.kt   → /api/v1/parent/onboarding/{metadata,child-info,preference-options}
        ├── ParentDashboardRouting.kt    → /api/v1/parent/dashboard
        ├── TrackProgressRouting.kt      → /api/v1/parent/track-progress
        └── ParentFeesRouting.kt         → /api/v1/parent/fees
```

Every routing file has a header comment with: purpose, endpoints, tables
touched, JWT requirements, and which UI screen consumes it.

---

## 2. Endpoint Index (alphabetical)

| Method | Path | Auth | Doc file |
|---|---|---|---|
| GET    | `/`                                              | public | (liveness) |
| GET    | `/api/v1/content/landing`                        | public | [01-landing.md](./01-landing.md) |
| GET    | `/api/v1/config/app-status`                      | public | [02-app-status.md](./02-app-status.md) |
| POST   | `/api/v1/auth/check-user`                        | public | [03-auth.md](./03-auth.md) |
| POST   | `/api/v1/auth/signup`                            | public | [03-auth.md](./03-auth.md) |
| POST   | `/api/v1/auth/login`                             | public | [03-auth.md](./03-auth.md) |
| POST   | `/api/v1/auth/send-otp`                          | public | [03-auth.md](./03-auth.md) |
| GET    | `/api/v1/user/details`                           | JWT    | [04-user-details.md](./04-user-details.md) |
| GET    | `/api/v1/onboarding/step`                        | JWT    | [05-onboarding.md](./05-onboarding.md) |
| GET    | `/api/v1/onboarding/academic/class-details`      | JWT    | [05-onboarding.md](./05-onboarding.md) |
| POST   | `/api/v1/onboarding/submit`                      | JWT    | [05-onboarding.md](./05-onboarding.md) |
| GET    | `/api/v1/school/announcements`                   | JWT    | [06-announcements.md](./06-announcements.md) |
| GET    | `/api/v1/school/announcements/search`            | JWT    | [06-announcements.md](./06-announcements.md) |
| POST   | `/api/v1/school/announcements/sync-whatsapp`     | JWT    | [06-announcements.md](./06-announcements.md) |
| GET    | `/api/v1/admissions/enquiries/summary`           | JWT    | [07-admissions.md](./07-admissions.md) |
| GET    | `/api/v1/admissions/enquiries`                   | JWT    | [07-admissions.md](./07-admissions.md) |
| GET    | `/api/v1/user/profile`                           | JWT    | [08-user-profile.md](./08-user-profile.md) |
| PUT    | `/api/v1/user/profile/philosophy`                | JWT    | [08-user-profile.md](./08-user-profile.md) |
| PUT    | `/api/v1/user/profile/tour-videos`               | JWT    | [08-user-profile.md](./08-user-profile.md) |
| PUT    | `/api/v1/user/profile/gallery`                   | JWT    | [08-user-profile.md](./08-user-profile.md) |
| GET    | `/api/v1/school/analytics`                       | JWT    | [09-drawer.md](./09-drawer.md) |
| GET    | `/api/v1/school/calendar`                        | JWT    | [09-drawer.md](./09-drawer.md) |
| GET    | `/api/v1/school/holidays`                        | JWT    | [09-drawer.md](./09-drawer.md) |
| GET    | `/api/v1/school/attendance/daily`                | JWT    | [09-drawer.md](./09-drawer.md) |
| GET    | `/api/v1/parent/onboarding/metadata`             | JWT    | [12-parent-ecosystem.md](./12-parent-ecosystem.md) |
| POST   | `/api/v1/parent/onboarding/child-info`           | JWT    | [12-parent-ecosystem.md](./12-parent-ecosystem.md) |
| GET    | `/api/v1/parent/onboarding/preference-options`   | JWT    | [12-parent-ecosystem.md](./12-parent-ecosystem.md) |
| GET    | `/api/v1/parent/dashboard`                       | JWT    | [12-parent-ecosystem.md](./12-parent-ecosystem.md) |
| GET    | `/api/v1/parent/track-progress`                  | JWT    | [12-parent-ecosystem.md](./12-parent-ecosystem.md) |
| GET    | `/api/v1/parent/fees`                            | JWT    | [12-parent-ecosystem.md](./12-parent-ecosystem.md) |
| GET    | `/api/v1/content/support`                        | public | [12-parent-ecosystem.md](./12-parent-ecosystem.md) |
| GET    | `/api/v1/school/dashboard`                       | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| GET    | `/api/v1/school/analytics/overview`              | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| GET    | `/api/v1/school/analytics/class-performance`     | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| GET    | `/api/v1/school/analytics/teacher-performance`   | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| GET    | `/api/v1/school/analytics/student/{studentId}`   | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| GET    | `/api/v1/school/analytics/syllabus-coverage`     | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| GET    | `/api/v1/school/leave-requests`                  | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| POST   | `/api/v1/school/leave-requests`                  | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| PATCH  | `/api/v1/school/leave-requests/{id}/status`      | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| GET    | `/api/v1/school/ptm`                             | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| POST   | `/api/v1/school/ptm`                             | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| GET    | `/api/v1/school/messages/threads`                | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| POST   | `/api/v1/school/messages/threads/{id}/read`      | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| POST   | `/api/v1/school/messages`                        | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| GET    | `/api/v1/school/results`                         | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |
| POST   | `/api/v1/school/results`                         | JWT    | [13-school-ecosystem.md](./13-school-ecosystem.md) |

Legacy `/auth/*` (without `/api/v1` prefix) are also wired and resolve to the
same handlers, so the existing `shared/.../AuthApi.kt` keeps working until the
mobile team migrates.

---

## 3. Common response envelope

Every endpoint returns one of these two shapes:

**Success**
```json
{ "success": true,  "message": "…", "data": { … } }
```
**Failure**
```json
{ "success": false, "message": "…", "errorCode": "OPTIONAL" }
```

HTTP codes follow the spec: `200 OK`, `201 Created` (signup), `202 Accepted`
(sync-whatsapp), `400`, `401`, `404`, `409`, `500`.

---

## 4. Authentication

- Algorithm: **HMAC256**
- Library:   `io.ktor:ktor-server-auth-jwt` (Auth0 java-jwt under the hood)
- Issuer:    `vidyaprayag-api` (override via `JWT_ISSUER`)
- Audience:  `vidyaprayag-app` (override via `JWT_AUDIENCE`)
- Lifetime:  access token 7 days, refresh token 30 days (env-overridable)
- Claims: `sub` = userId UUID, `role` = ADMIN|PARENT|TEACHER, `name`

Mobile client must send:
```
Authorization: Bearer <jwt>
```

---

## 5. Database

Local dev: **SQLite** file `data.db` in the working directory — zero-config.
Production: **Postgres** — set `DATABASE_URL` env (Supabase / Render / etc.).

Tables auto-migrate via `SchemaUtils.createMissingTablesAndColumns` at boot,
so adding a column to a Table object just works (no migration scripts).

See [`10-database.md`](./10-database.md) for the full schema reference.

On first boot, `Seed.kt` populates:
- Landing CMS strings
- App-config flags
- One demo `St. Xavier Academy` school + its philosophy, media, classes,
  3 announcements, 5 admission enquiries, calendar events, holidays,
  6 students, 2 faculty, and today's attendance records.

---

## 6. Manual / DevOps actions still needed from you

See [`MANUAL_STEPS.md`](./MANUAL_STEPS.md).

---

## 7. Change log

See [`CHANGELOG.md`](./CHANGELOG.md) for a per-commit summary of what was
added in `backend-by-abuzar`.
