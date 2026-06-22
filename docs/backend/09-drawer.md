# 09 — Drawer Options (Analytics / Calendar / Holidays / Attendance)

**File:** `server/src/main/kotlin/com.littlebridge.enrollplus/feature/school/SchoolRouting.kt`
**Spec ref:** `vidya_prayag_api_spec2.artifact.md` §Screen: Drawer Options
**UI consumers:**
- `composeApp/.../ui/screens/admin/AnalyticsDashboardScreen.kt`
- `composeApp/.../ui/screens/admin/AcademicCalendarScreen.kt`
- `composeApp/.../ui/screens/admin/DailyAttendanceScreen.kt`

---

## Endpoints (all JWT-protected)

```
GET /api/v1/school/analytics
GET /api/v1/school/calendar?date=YYYY-MM-DD&view_type=week|month&standard=…
GET /api/v1/school/holidays?filter_type=weekly|monthly|yearly
GET /api/v1/school/attendance/daily?type=student|faculty&grade=Grade%205
```

---

## 1. Analytics (Coming Soon)

```json
{
  "success": true,
  "message": "Analytics feature is coming soon!",
  "data": { "is_available": false, "expected_release": "Q3 2024" }
}
```
No DB call — a static placeholder per the spec. Replace this with real
aggregations once the analytics module is built.

## 2. Calendar

```json
{
  "success": true,
  "data": {
    "calendar_events": [
      { "date": "2024-05-01", "day": "Wednesday", "event_id": "CAL_101",
        "event_title": "May Day", "event_description": "Public holiday on account of International Labour Day." }
    ],
    "summary": { "total_working_days": 22, "public_holidays": 1, "school_holidays": 0 }
  }
}
```

**Filtering:**
- `view_type=week`  → events within ±3 days of `date`
- `view_type=month` → events in same calendar month as `date` (default)
- `standard` (optional) → filters to events tagged for that grade OR untagged
  (global) events. Untagged events show up for every grade.

**summary calculation** (for the current month):
- `public_holidays`  = count of `holiday_list` rows with type='Public'
- `school_holidays`  = count of `holiday_list` rows with type='School'
- `total_working_days` = daysInMonth − public − school − weekend days
  (Sat/Sun)

## 3. Holidays

```json
{
  "success": true,
  "data": {
    "holidays": [
      { "date": "2024-01-26", "title": "Republic Day", "type": "Public" },
      { "date": "2024-08-15", "title": "Independence Day", "type": "Public" }
    ]
  }
}
```
`filter_type` is `weekly | monthly | yearly`, defaults to `yearly`. It filters
on the `holiday_list.frequency` column.

## 4. Daily Attendance

```json
{
  "success": true,
  "data": {
    "type": "student",
    "grade": "Grade 5",
    "present_count": 5,
    "absent_count": 1,
    "total_count": 6,
    "attendance_percentage": "83.3%",
    "attendance_list": [
      { "profile_pic": "https://…/st_501.jpg", "name": "Aarav Sharma", "id": "ST_501", "status": "present" },
      { "profile_pic": "https://…/st_502.jpg", "name": "Isha Kapoor",  "id": "ST_502", "status": "absent"  }
    ]
  }
}
```

**Notes:**
- The date is implicitly `LocalDate.now()` (today). A future iteration can
  accept a `?date=` query param for historical lookups.
- `grade` is **required** when `type=student`, **forbidden** when `type=faculty`
  (response sets `grade: null`).
- Returns 400 if `type` is anything other than `student` / `faculty`.

## DB tables touched

| Table | Operation |
|---|---|
| `academic_calendar` | READ (calendar) |
| `holiday_list`      | READ (calendar summary + /holidays) |
| `attendance_records`| READ (attendance/daily) |
| `students`          | READ join for name/profile_pic |
| `faculty`           | READ join for name/profile_pic |

## Errors

| Code | Meaning |
|---|---|
| 401  | JWT missing/invalid |
| 404  | User not associated with any school |
| 400  | Invalid `date` format or invalid `type` |
