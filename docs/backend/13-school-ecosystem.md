# 13 — School Ecosystem

**Source spec:** `school_api_spec.artifact.md`
**Branch:** `backend-by-abuzar`
**Implementation:**
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/school/SchoolDashboardRouting.kt`
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/school/SchoolAnalyticsRouting.kt`
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/school/LeaveRequestsRouting.kt`
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/school/PtmRouting.kt`
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/school/MessagesRouting.kt`
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/school/ResultsRouting.kt`

All UI strings, statistics, and reference lists are **backend-driven**. Defaults are seeded into `app_config` (KV) by `db/Seed.kt`; ops edit them directly in Supabase without redeploying. Live numbers are computed against real tables (`attendance_records`, `students`, `faculty`, `school_onboarding_drafts`, `exam_results`, `leave_requests`, `ptm_events`, `ptm_class_progress`, `message_threads`, `messages`) on every request.

---

## Endpoints

| Method | Path                                                       | Auth   |
|--------|------------------------------------------------------------|--------|
| GET    | `/api/v1/school/dashboard`                                 | JWT    |
| GET    | `/api/v1/school/analytics/overview`                        | JWT    |
| GET    | `/api/v1/school/analytics/class-performance?class=`        | JWT    |
| GET    | `/api/v1/school/analytics/teacher-performance`             | JWT    |
| GET    | `/api/v1/school/analytics/student/{studentId}`             | JWT    |
| GET    | `/api/v1/school/analytics/syllabus-coverage`               | JWT    |
| GET    | `/api/v1/school/leave-requests?type=&status=`              | JWT    |
| POST   | `/api/v1/school/leave-requests`                            | JWT    |
| PATCH  | `/api/v1/school/leave-requests/{id}/status`                | JWT    |
| GET    | `/api/v1/school/ptm`                                       | JWT    |
| POST   | `/api/v1/school/ptm`                                       | JWT    |
| GET    | `/api/v1/school/messages/threads`                          | JWT    |
| POST   | `/api/v1/school/messages/threads/{id}/read`                | JWT    |
| POST   | `/api/v1/school/messages`                                  | JWT    |
| GET    | `/api/v1/school/results?test=&class=&subject=`             | JWT    |
| POST   | `/api/v1/school/results`                                   | JWT    |

All JSON responses use the canonical envelope `{ success, message, data }`.

---

## 1. GET `/api/v1/school/dashboard`

Welcome banner + 4-step onboarding progress (joined live with `school_onboarding_drafts`) + support card.

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
      "video_url": "https://…"
    }
  }
}
```

**Compute rules**
- `welcome` ← CMS `school_dashboard_welcome`.
- `steps` ← CMS `school_dashboard_steps`; mapping `{1:BASIC, 2:BRANDING, 3:ACADEMIC, 4:REVIEW}` against `school_onboarding_drafts.step_type` decides `is_completed`.
- `progress = completed_steps / total_steps`.
- `status_label`: `"Not Configured"` | `"Pending"` | `"In Progress"` | `"Complete"`.
- `support` ← CMS `school_dashboard_support`.

---

## 2. GET `/api/v1/school/analytics/overview`

```json
{
  "success": true,
  "message": "Analytics overview fetched successfully",
  "data": {
    "performance_trend": [0.4, 0.55, 0.48, 0.85, 0.65, 0.75],
    "current_growth": "+4.2%",
    "cards": [
      { "title": "Student Tracking",       "value": "94%", "sub_value": "Avg Attendance",  "icon_url": "https://…", "trend": null },
      { "title": "Syllabus Coverage",      "value": "78%", "sub_value": "Logged Progress", "icon_url": "https://…", "trend": null },
      { "title": "Teacher Accountability", "value": "4.8", "sub_value": "Avg Rating",      "icon_url": "https://…", "trend": null },
      { "title": "Class Performance",      "value": "82%", "sub_value": "Proficiency",     "icon_url": "https://…", "trend": null }
    ],
    "insights": [
      { "title": "Attendance Peak", "description": "Class 10-A reached 99% attendance", "icon_name": "trending_up", "icon_color": "#10B981" }
    ]
  }
}
```

**Compute rules**
- `performance_trend`, `current_growth` ← CMS `school_analytics_overview`.
- `cards` ← CMS `school_analytics_cards_template`. **card[0].value is overlaid** with live `avg attendance %` (last 7 days from `attendance_records`, school-scoped).
- `insights` ← CMS `school_analytics_insights`.

---

## 3. GET `/api/v1/school/analytics/class-performance?class=Grade%2010-A`

Returns the `school_class_performance` CMS blob verbatim with `summary.active_students` overlaid by the live count of `students` rows (school + optional `class` filter).

```json
{
  "success": true,
  "message": "Class performance fetched successfully",
  "data": {
    "grade_distribution": [ { "grade": "F", "percentage": 12, "value": 0.20 } ],
    "summary": { "avg_proficiency": "78.4%", "active_students": 428, "median_grade": "B+" },
    "subject_matrix": [ { "name": "Mathematics", "percentage": 82, "trend": "up" } ],
    "risk": { "critical_count": 12, "moderate_count": 28, "proficiency_target_reach": 75 },
    "top_performer": { "name": "Elena Rodriguez", "details": "GPA: 3.98 • Grade 11-B" },
    "recent_progress": [ { "name": "Jordan Davis", "initials": "JD", "math": "92%", "science": "88%", "literature": "85%", "attendance": "98%", "status": "EXCELLING" } ]
  }
}
```

---

## 4. GET `/api/v1/school/analytics/teacher-performance`

Returns the `school_teacher_performance` CMS blob. `star_faculty` is overlaid with the top-3 active rows from the live `faculty` table.

```json
{
  "success": true,
  "message": "Teacher performance fetched successfully",
  "data": {
    "aggregate_compliance": "94.2%",
    "compliance_trend": "+2.4% from last month",
    "syllabus_update_trend": [0.4, 0.6, 0.5, 0.85, 0.7, 1.0, 0.5, 0.75],
    "star_faculty": [
      { "rank": 1, "name": "Dr. Sarah Jenkins", "department": "Mathematics", "score": 99.8, "image_url": null }
    ],
    "accountability_matrix": [
      { "id": "1", "name": "James Miller", "department": "Chemistry Dept.", "compliance_score": 92,
        "avg_update_delay": "1.2 Days", "student_avg_mark": "84.5%", "risk_correlation": "Stable", "initials": "JM" }
    ],
    "dept_efficiencies": [ { "name": "Science & Technology", "percentage": 96 } ]
  }
}
```

---

## 5. GET `/api/v1/school/analytics/student/{studentId}`

`studentId` accepts either the UUID primary key **or** the human-readable `student_code` (matches Results screen).

```json
{
  "success": true,
  "message": "Student analytics fetched successfully",
  "data": {
    "student": {
      "id": "ED-001",
      "name": "Marcus Holloway",
      "class": "Grade 10-A",
      "roll_number": "12",
      "profile_pic": null
    },
    "kpi": { "attendance": "92%", "average": "78.4%", "rank": 7 },
    "subjects": [ { "name": "Mathematics", "score": 92, "trend": "up" } ],
    "milestones": [ { "title": "Quarter 3 honors list", "date": "2025-09-01", "is_unlocked": true } ],
    "narrative": "Strong rebound in numeracy this quarter; literacy steady."
  }
}
```

**Compute rules**
- `student` ← `students` row (mandatory; 404 if missing).
- `kpi.attendance` ← live present% over last 30 days from `attendance_records`.
- `subjects` / `milestones` ← CMS `school_student_analytics_template`.
- `narrative` ← CMS `school_student_analytics_narrative`.

---

## 6. GET `/api/v1/school/analytics/syllabus-coverage`

Pure CMS — returns `school_syllabus_coverage` verbatim.

```json
{
  "success": true,
  "message": "Syllabus coverage fetched successfully",
  "data": {
    "overall": { "percentage": 78, "trend": "+2.1%" },
    "by_subject": [ { "name": "Mathematics", "percentage": 82, "behind_by_days": 0 } ],
    "by_class":   [ { "class": "Grade 10-A", "percentage": 88 } ]
  }
}
```

---

## 7. GET `/api/v1/school/leave-requests?type=student|teacher&status=Pending|Approved|Rejected`

```json
{
  "success": true,
  "message": "Leave requests fetched successfully",
  "data": {
    "type": "student",
    "approval_rate": 94,
    "weekly_count": 12,
    "requests": [
      {
        "id": "uuid",
        "requester_name": "Marcus Holloway",
        "requester_role": "student",
        "date_from": "2025-12-12",
        "date_to":   "2025-12-14",
        "date_range": "Dec 12 - Dec 14",
        "reason": "Family Function",
        "image_url": null,
        "status": "Pending"
      }
    ]
  }
}
```

**Compute rules** (always against the school slice, not the filtered slice)
- `approval_rate` = `100 * approved / (approved + rejected)` over the last 30 days; `0` when denominator is 0.
- `weekly_count` = requests created in the last 7 days.
- `date_range` is formatted server-side; year is omitted when both dates are in the current year.

---

## 8. POST `/api/v1/school/leave-requests`

**Request**
```json
{
  "requester_name": "Marcus Holloway",
  "requester_role": "student",
  "date_from": "2025-12-12",
  "date_to":   "2025-12-14",
  "reason": "Family Function",
  "image_url": null,
  "requester_id": null
}
```

**Response (201 Created)**
```json
{
  "success": true,
  "message": "Leave request created",
  "data": {
    "id": "uuid",
    "requester_name": "Marcus Holloway",
    "requester_role": "student",
    "date_from": "2025-12-12",
    "date_to":   "2025-12-14",
    "date_range": "Dec 12 - Dec 14",
    "reason": "Family Function",
    "image_url": null,
    "status": "Pending"
  }
}
```

`requester_role` must be `student` or `teacher` (case-insensitive; normalized to lower-case server-side).

---

## 9. PATCH `/api/v1/school/leave-requests/{id}/status`

**Request**
```json
{ "status": "Approved" }
```

`status` must be one of `Pending|Approved|Rejected` (case-insensitive; normalized to title-case server-side). Stamps `actioned_by` (JWT user) + `actioned_at`.

**Response**
```json
{ "success": true, "message": "Leave request status updated", "data": null }
```

---

## 10. GET `/api/v1/school/ptm`

```json
{
  "success": true,
  "message": "PTM data fetched successfully",
  "data": {
    "active_event": {
      "id": "uuid",
      "title": "School-Wide PTM",
      "date": "2025-10-28",
      "slot": "09:00 - 13:00",
      "expected_parents": 450,
      "checked_in_parents": 312,
      "invites_delivered": 96,
      "read_receipts": 78
    },
    "history": [
      { "id": "uuid", "date": "2025-09-15", "title": "Term 1 Performance Review", "turnout": 78, "total_met": 342 }
    ],
    "class_progress": [
      { "id": "uuid", "class_name": "10A", "teacher_name": "Ms. Sarah Jenkins", "met_count": 28, "total_count": 30, "progress": 0.93 }
    ]
  }
}
```

**Compute rules**
- `active_event` = next row with `date >= today` (soonest first); falls back to the most-recent past row.  Null if the school has never scheduled a PTM.
- `history` = the last 10 past PTMs (date `< today`, DESC).
- `class_progress` = `ptm_class_progress` rows belonging to `active_event.id`, sorted by `class_name`. `progress` is server-computed.

---

## 11. POST `/api/v1/school/ptm`

**Request**
```json
{
  "title": "Mid-Year PTM",
  "date": "2026-01-15",
  "slot": "10:00 - 14:00",
  "expected_parents": 600
}
```

`date` must be `YYYY-MM-DD`. Counters (`checked_in_parents`, `invites_delivered`, `read_receipts`, `turnout`, `total_met`) start at 0.

**Response (201 Created)** — same shape as `active_event` above.

---

## 12. GET `/api/v1/school/messages/threads`

Owner-scoped (`owner_user_id = JWT.sub`).

```json
{
  "success": true,
  "message": "Threads fetched successfully",
  "data": {
    "threads": [
      {
        "id": "uuid",
        "sender_name": "Mr. Adrian Chen",
        "sender_role": "Class 10-A Teacher",
        "last_message": "The project submission is due tomorrow…",
        "time": "10:45 AM",
        "unread_count": 2,
        "sender_image_url": null,
        "icon_name": null,
        "is_read": false
      }
    ]
  }
}
```

`time` is server-formatted: today → `"h:mm a"`, yesterday → `"Yesterday"`, older → `"MMM dd"`.

---

## 13. POST `/api/v1/school/messages/threads/{id}/read`

Sets `unread_count = 0`, `is_read = true` for the thread.  Only succeeds if the thread is owned by the authenticated user.

**Response**
```json
{ "success": true, "message": "Thread marked as read", "data": null }
```

---

## 14. POST `/api/v1/school/messages`

**Request (new thread)**
```json
{
  "thread_id": null,
  "sender_name": "Admin Desk",
  "sender_role": "Support",
  "recipient_user_id": null,
  "body": "Welcome to the admin inbox — system message."
}
```

When `thread_id` is `null`, a new thread is created on the fly. `recipient_user_id` defaults to the authenticated user (useful for system alerts).

**Request (reply on existing thread)**
```json
{ "thread_id": "uuid-from-{{thread_id}}", "body": "Got it — thanks." }
```

When `thread_id` is set, the thread's `last_message` / `last_message_at` are refreshed and `is_read` is flipped to true (the sender already saw their own message).

**Response (201 Created)**
```json
{
  "success": true,
  "message": "Message sent",
  "data": { "thread_id": "uuid", "message_id": "uuid" }
}
```

---

## 15. GET `/api/v1/school/results?test=Unit%20Test%20II&class=Grade%2010-A&subject=Mathematics`

When any filter is missing, the server falls back to the first entry of the matching `available_*` list (CMS-driven), so the UI never gets a 400 for an empty dropdown.

```json
{
  "success": true,
  "message": "Results fetched successfully",
  "data": {
    "filters": {
      "selected_test": "Unit Test II",
      "selected_class": "Grade 10-A",
      "selected_subject": "Mathematics",
      "available_tests":   ["Unit Test I","Unit Test II","Mid Term","Final"],
      "available_classes": ["Grade 10-A","Grade 10-B","Grade 11-A","Grade 11-B"],
      "available_subjects":["Mathematics","Science","Literature","History","Chemistry","Physics"]
    },
    "summary": {
      "class_average": "85.0",
      "average_trend": "+0.0%",
      "exceeding_count": 1,
      "meeting_count": 1,
      "below_count": 0
    },
    "students": [
      { "id": "ED-001", "name": "Marcus Holloway", "image_url": null,
        "attendance": "100%", "score": "98", "status": "Exceeding", "trend": "+2.4%" }
    ]
  }
}
```

**Compute rules**
- `available_*` ← CMS `school_results_filters`.
- `class_average` = mean of numeric `score` values (non-numeric scores like `"A+"` / `"Pending"` are ignored).  Formatted to one decimal.
- `exceeding_count` / `meeting_count` / `below_count` are case-insensitive counts of `status`.
- `average_trend` is currently a placeholder (`"+0.0%"`) until the analytics pipeline lands.

---

## 16. POST `/api/v1/school/results` (bulk publish)

**Request**
```json
{
  "test": "Unit Test II",
  "class": "Grade 10-A",
  "subject": "Mathematics",
  "results": [
    { "student_id": "ED-001", "student_name": "Marcus Holloway", "score": "98", "attendance": "100%", "status": "Exceeding", "trend": "+2.4%" },
    { "student_id": "ED-002", "student_name": "Sarah Miller",    "score": "72", "attendance": "88%",  "status": "Meeting",   "trend": "+0.5%" }
  ]
}
```

- Portable upsert against the unique tuple `(school_id, test, class_name, subject, student_id)`.
- Idempotent — publishing the same test twice updates rather than duplicates.
- `student_name` is optional; if missing, the server looks it up via `students.student_code`. If still not found, `student_id` is used as the name (graceful fallback).
- `attendance` / `status` / `trend` default to `"0%"`, `"Pending"`, `"0%"`.

**Response (201 Created)**
```json
{ "success": true, "message": "Results published", "data": { "upserted": 2 } }
```

---

## Database tables introduced

| Table                | Purpose                                              |
|----------------------|------------------------------------------------------|
| `leave_requests`     | Student / teacher leave applications                 |
| `ptm_events`         | PTM master record (title/date/slot/turnout stats)    |
| `ptm_class_progress` | Per-class PTM "met / total" rollup                   |
| `message_threads`    | Admin-inbox thread metadata                          |
| `messages`           | Individual messages within a thread                  |
| `exam_results`       | Per-student results upserted by Results screen       |

All registered in `db/DatabaseFactory.kt#allTables` so SQLite local dev creates them automatically; for Supabase Postgres set `AUTO_CREATE_TABLES=true` once or run the matching DDL by hand.

---

## CMS keys (idempotently seeded in `db/Seed.kt`)

```
school_dashboard_welcome
school_dashboard_steps
school_dashboard_support
school_analytics_overview
school_analytics_cards_template
school_analytics_insights
school_class_performance
school_teacher_performance
school_student_analytics_template
school_student_analytics_narrative
school_syllabus_coverage
school_results_filters
```

All are **insert-if-missing only** — operators can hand-edit them in Supabase → `app_config` and never lose changes on backend redeploys.

---

## Errors

| HTTP | Code | Trigger                                                   |
|------|------|-----------------------------------------------------------|
| 400  | —    | `requester_role`, `status`, `date`, or required body fields invalid / missing |
| 401  | —    | Missing or invalid JWT                                     |
| 404  | —    | Student not found · leave request not found · thread not found · user has no `school_id` |

All errors use the standard `{ success: false, message, error_code? }` envelope from `core/ResponseExtensions.kt#fail`.
