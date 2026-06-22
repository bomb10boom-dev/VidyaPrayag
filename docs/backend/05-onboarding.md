# 05 — School Onboarding Flow

**File:** `server/src/main/kotlin/com.littlebridge.enrollplus/feature/onboarding/OnboardingRouting.kt`
**Spec ref:** `vidya_prayag_api_spec.artifact.md` §Module: School Onboarding Flow (ADMIN)
**UI consumers:**
- `composeApp/.../ui/screens/admin/InstitutionalBasicOBScreen.kt`
- `composeApp/.../ui/screens/admin/BrandingInfoOBScreen.kt`
- `composeApp/.../ui/screens/admin/AcademicInfoOBScreen.kt`
- `composeApp/.../ui/screens/admin/LaunchInfoOBScreen.kt`

---

## Endpoints

```
GET  /api/v1/onboarding/step?obStepType={BASIC|BRANDING|ACADEMIC|REVIEW}
GET  /api/v1/onboarding/academic/class-details?classId={code}
POST /api/v1/onboarding/submit
```
All endpoints require `Authorization: Bearer <jwt>`.

---

## 1. GET /step?obStepType=BASIC

Returns the field schema + any draft values previously saved for this step.

```json
{
  "success": true,
  "data": {
    "ob_step_type": "BASIC",
    "current_step_count": 1,
    "total_step_count": 4,
    "step_name": "Institutional Basics",
    "step_icon": "school",
    "step_heading": "Establish identity.",
    "list_of_data": [
      { "key": "school_name", "type": "SchoolName",
        "draft_exists": true, "draft_value": "St. Xavier Academy",
        "input_type": "line" },
      { "key": "board_affiliation", "type": "BoardAffiliation",
        "draft_exists": false, "draft_value": null, "input_type": "dropdown" }
    ]
  }
}
```

Field set per step:

| Step | Fields |
|---|---|
| BASIC    | school_name, board_affiliation, official_email, contact_number, country_code, address |
| BRANDING | logo_url, theme_color |
| ACADEMIC | (returns `list_of_active_classes` instead of `list_of_data`) |
| REVIEW   | (returns `identity_details` + `compliance_docs` + `list_of_selected_modules`) |

---

## 2. GET /step?obStepType=ACADEMIC

```json
{
  "success": true,
  "data": {
    "ob_step_type": "ACADEMIC",
    "current_step_count": 3,
    "total_step_count": 4,
    "list_of_active_classes": [
      { "id": "C10", "name": "Grade 10", "sections": ["A","B"] }
    ]
  }
}
```

## 3. GET /academic/class-details?classId=C10

```json
{
  "success": true,
  "data": {
    "class_id": "C10",
    "class_name": "Grade 10",
    "total_subjects": 5,
    "list_of_subjects": [
      { "sub_name": "Modern Physics", "sub_code": "PHY-10", "teacher_assigned": "Dr. Robert Chen" }
    ]
  }
}
```

## 4. GET /step?obStepType=REVIEW

```json
{
  "success": true,
  "data": {
    "ob_step_type": "REVIEW",
    "identity_details": { "institution_name": "St. Xavier Academy", "is_verified": false },
    "compliance_docs": [ { "doc_id": "d_1", "doc_name": "Affiliation Cert", "is_verified": true } ],
    "list_of_selected_modules": [ { "name": "Analytics", "isSelected": true } ]
  }
}
```

> NOTE: `compliance_docs` is currently a stub — when the doc-upload flow lands
> we will read from a new `compliance_document_vault` table. Flagged TODO in
> code.

---

## 5. POST /submit

```json
{
  "ob_step_type": "BASIC",
  "is_final_submission": false,
  "data_payload": {
    "school_name": "St. Xavier Academy",
    "board_affiliation": "CBSE",
    "official_email": "info@stxavier.edu",
    "contact_number": "9876543210",
    "country_code": "+91",
    "address": "Education Lane, Sector 42, New Delhi"
  }
}
```

**Response 200**
```json
{
  "success": true,
  "message": "Step processed successfully",
  "data": {
    "next_step": "BRANDING",
    "is_onboarding_complete": false,
    "redirect_to_home": false
  }
}
```

Final REVIEW submission (`is_final_submission: true`, `ob_step_type: "REVIEW"`)
promotes BASIC + BRANDING drafts into the `schools` table and flips
`schools.onboarding_status` to `COMPLETED`. The response:

```json
{
  "data": { "next_step": null, "is_onboarding_complete": true, "redirect_to_home": true }
}
```

## DB tables touched

| Table | Operation |
|---|---|
| `school_onboarding_drafts` | DELETE-then-INSERT (upsert) per (user,step,key) |
| `schools`                  | INSERT or UPDATE on final REVIEW |
| `school_classes`           | READ (for ACADEMIC) |
| `school_subjects`          | READ (for class-details) |
