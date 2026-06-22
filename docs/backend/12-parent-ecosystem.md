# 12 — Parent Ecosystem

**Source spec:** `parent_api_spec.artifact.md`
**Branch:** `backend-by-abuzar`
**Implementation:**
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/parent/ParentOnboardingRouting.kt`
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/parent/ParentDashboardRouting.kt`
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/parent/TrackProgressRouting.kt`
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/parent/ParentFeesRouting.kt`
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/content/SupportRouting.kt`

All UI strings, statistics, and configurations are **backend-driven**. Defaults are seeded into `app_config` (KV) by `CmsSeed.ensureLandingAndConfig()`; ops can edit them directly in Supabase without redeploying.

---

## Endpoints

| Method | Path                                            | Auth   |
|--------|-------------------------------------------------|--------|
| GET    | `/api/v1/parent/onboarding/metadata`            | JWT    |
| POST   | `/api/v1/parent/onboarding/child-info`          | JWT    |
| GET    | `/api/v1/parent/onboarding/preference-options`  | JWT    |
| GET    | `/api/v1/parent/dashboard`                      | JWT    |
| GET    | `/api/v1/parent/track-progress`                 | JWT    |
| GET    | `/api/v1/parent/fees`                           | JWT    |
| GET    | `/api/v1/content/support`                       | Public |

All JSON responses use the canonical envelope `{ success, message, data }`.

---

## 1. GET `/api/v1/parent/onboarding/metadata`

Returns the dropdown reference data and header copy for Child Onboarding Step 1.

```json
{
  "success": true,
  "message": "Onboarding metadata fetched",
  "data": {
    "screen_config": {
      "header_title": "Let's build a profile for your child.",
      "header_subtitle": "We use this information to curate the best learning path.",
      "progress_label": "Step 1 of 3",
      "progress_value": 0.33
    },
    "available_grades": ["Grade 1","Grade 2","Grade 3","Nursery","KG"],
    "available_interests": ["Music","Art","STEM","Sports","Languages"],
    "footer_text": "Your data is encrypted and secure."
  }
}
```

**CMS keys driving this response:** `parent_onboarding_step1_screen_config`, `parent_available_grades`, `parent_available_interests`, `parent_onboarding_footer_text`.

---

## 2. POST `/api/v1/parent/onboarding/child-info`

Creates a row in `children` for the JWT-authenticated parent.

**Request**
```json
{
  "child_name": "Aarav Sharma",
  "date_of_birth": "2018-05-15",
  "gender": "MALE",
  "current_grade": "Grade 1",
  "interests": ["Music", "STEM"]
}
```

**Response (201 Created)**
```json
{
  "success": true,
  "message": "Child profile created successfully",
  "data": {
    "child_id": "8a4e…-uuid",
    "child_name": "Aarav Sharma",
    "next_step": "PREFERENCES"
  }
}
```

`child_name` is required; everything else is optional and stored as-is.

---

## 3. GET `/api/v1/parent/onboarding/preference-options`

Step 2 metadata: boards, focus areas, budget slider config.

```json
{
  "success": true,
  "message": "Preference options fetched",
  "data": {
    "available_boards": ["CBSE","ICSE","IB","State Board"],
    "available_focus_areas": [
      { "id": "acad", "title": "Academics", "icon": "school" },
      { "id": "sports", "title": "Sports", "icon": "sports_soccer" }
    ],
    "budget_config": {
      "min_value": 0, "max_value": 10000,
      "default_range": [2000, 5000],
      "currency_symbol": "$"
    }
  }
}
```

**CMS keys:** `parent_available_boards`, `parent_available_focus_areas`, `parent_budget_config`.

---

## 4. GET `/api/v1/parent/dashboard`

Home-screen "handshake" API.

```json
{
  "success": true,
  "message": "Dashboard fetched successfully",
  "data": {
    "greeting": "Good Morning, Arjun",
    "child_summary": {
      "id": "uuid",
      "name": "Aarav",
      "overall_progress": 0.75,
      "current_level": 4,
      "attendance_status": "PRESENT",
      "profile_pic": null
    },
    "alerts": [
      { "id": "fees_overdue", "title": "Fees Due", "value": "$1,200", "type": "CRITICAL" },
      { "id": "ptm_upcoming", "title": "Upcoming PTM", "value": "Nov 25", "type": "INFO" }
    ],
    "featured_schools": [
      { "id": "uuid", "name": "St. Xavier", "rating": 4.5, "location": "Noida", "image": "..." }
    ],
    "curation_logic": "Curation aligned with NEP 2020 developmental milestones."
  }
}
```

**Compute rules:**
- `greeting` = time-of-day (Good Morning/Afternoon/Evening) + first name (from JWT `name` claim).
- `child_summary` = first **active** child of the parent (ordered by `created_at` ASC). `null` if none.
- `alerts[CRITICAL]` = SUM(amount) of `fee_records` with `status='OVERDUE'`, formatted as currency string.
- `alerts[INFO/WARNING]` = appended from CMS key `parent_dashboard_info_alerts`.
- `featured_schools` = top 5 active schools.
- `curation_logic` = CMS key `parent_dashboard_curation_logic`.

---

## 5. GET `/api/v1/parent/track-progress`

Holistic Growth screen.

```json
{
  "success": true,
  "message": "Holistic progress fetched successfully",
  "data": {
    "hero_section": {
      "progress_percentage": 75,
      "level_label": "LEVEL 4 REACHED",
      "journey_description": "On track for Grade 2 transition"
    },
    "badges": [ { "title": "Social Star", "icon": "workspace_premium", "is_locked": false, "colors": ["#B6C7EB","#006C49"] } ],
    "academic_core": {
      "label": "NEP ALIGNED",
      "competencies": [ { "title": "Literacy", "progress": 0.85, "icon": "translate" } ]
    },
    "emotional_intelligence": {
      "description": "Significant growth in Social Interaction this month.",
      "metrics": { "Empathy": 0.8, "Resilience": 0.7, "Social": 0.9 }
    },
    "play_discovery": [
      { "title": "Agility", "description": "Gross motor met", "image": null, "status": "MET" }
    ],
    "last_updated": "Today, 10:45 AM"
  }
}
```

**Compute rules:**
- `progress_percentage` = `children.overall_progress * 100` (rounded down).
- `level_label` = `"LEVEL ${children.current_level} REACHED"`.
- `journey_description` = `"On track for ${children.current_grade} transition"` when the child has a grade; otherwise the CMS template `parent_track_journey_description`.
- All other sections come from CMS keys: `parent_track_badges`, `parent_track_academic_label`, `parent_track_academic_competencies`, `parent_track_ei_description`, `parent_track_ei_metrics`, `parent_track_play_discovery`.

---

## 6. GET `/api/v1/parent/fees`

```json
{
  "success": true,
  "message": "Fee status fetched successfully",
  "data": {
    "stats": {
      "total_collected": "$428,500",
      "progress": 0.85,
      "outstanding": "$72,120",
      "overdue_count": 145
    },
    "announcements": [
      { "id": "f1", "title": "Deadline", "time": "2h ago", "desc": "Submit Q3 fees.", "type": "Payment" }
    ]
  }
}
```

**Aggregation rules** (against `fee_records` filtered by `parent_id = JWT.sub`):
- `total_collected` = SUM(`amount`) where `status='PAID'`, formatted with currency symbol.
- `outstanding` = SUM(`amount`) where `status IN ('DUE','OVERDUE')`.
- `overdue_count` = COUNT(*) where `status='OVERDUE'`.
- `progress` = `total_collected / (total_collected + outstanding)`, coerced to `[0.0, 1.0]`. Returns `0.0` when there are no rows.

**Announcements** are CMS-driven via `parent_fees_announcements`.

---

## 7. GET `/api/v1/content/support`

Public endpoint for the Drawer & Legal screen.

```json
{
  "success": true,
  "message": "Support config fetched",
  "data": {
    "support_contact": "+91-9876543210",
    "categories": ["TECHNICAL","ACADEMIC","ADMISSIONS","FEES"],
    "help_center_url": "https://vidyaprayag.com/help"
  }
}
```

**CMS key:** `parent_support_config`.

---

## Database schema

Two new operational tables (auto-created on SQLite, migrate manually on Supabase):

```sql
-- children
CREATE TABLE children (
  id                 UUID PRIMARY KEY,
  parent_id          UUID NOT NULL,
  school_id          UUID NULL,
  child_name         TEXT NOT NULL,
  date_of_birth      VARCHAR(12),         -- YYYY-MM-DD
  gender             VARCHAR(16),         -- MALE | FEMALE | OTHER
  current_grade      VARCHAR(32),
  interests          TEXT NOT NULL DEFAULT '[]',  -- JSON array
  profile_pic        TEXT,
  overall_progress   DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  current_level      INTEGER NOT NULL DEFAULT 1,
  attendance_status  VARCHAR(16) NOT NULL DEFAULT 'PRESENT',
  is_active          BOOLEAN NOT NULL DEFAULT TRUE,
  created_at         TIMESTAMP NOT NULL,
  updated_at         TIMESTAMP NOT NULL
);

-- fee_records
CREATE TABLE fee_records (
  id           UUID PRIMARY KEY,
  parent_id    UUID NOT NULL,
  child_id     UUID NULL,
  school_id    UUID NULL,
  title        TEXT NOT NULL,
  description  TEXT,
  amount       DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  currency     VARCHAR(8) NOT NULL DEFAULT 'USD',
  due_date     VARCHAR(12),
  status       VARCHAR(16) NOT NULL DEFAULT 'DUE',     -- PAID | DUE | OVERDUE
  category     VARCHAR(32) NOT NULL DEFAULT 'Tuition',
  created_at   TIMESTAMP NOT NULL,
  updated_at   TIMESTAMP NOT NULL
);
```

> On SQLite dev these are created automatically by `SchemaUtils.createMissingTablesAndColumns`. On Supabase, set `AUTO_CREATE_TABLES=true` (one-off) or apply the SQL manually.

---

## Analytics events (per spec)

The spec lists `onboarding_abandoned`, `payment_failed`, `report_downloaded`. These are emitted by the **client** (Compose Multiplatform) — the backend exposes the data they need but does not generate the events itself.
