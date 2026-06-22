# 04 — User Details & Onboarding State

**File:** `server/src/main/kotlin/com.littlebridge.enrollplus/feature/user/UserDetailsRouting.kt`
**Spec ref:** `vidya_prayag_api_spec.artifact.md` §Module: Role-Specific Experience
**UI consumers:**
- Post-login routing in `shared/.../presentation/MainViewModel.kt`
- Side-drawer / theme toggle in `composeApp/.../ui/components/VidyaPrayagDrawer.kt`

---

## Endpoint

```
GET /api/v1/user/details
Authorization: Bearer <jwt>
```

## Response (200 OK)

```json
{
  "success": true,
  "data": {
    "personal_details": {
      "role": "ADMIN",
      "id": "uuid",
      "name": "Arjun Sharma",
      "profile_pic": "https://…",
      "email": "arjun@stxavier.edu",
      "mobile": "9876543210"
    },
    "onboarding_details": {
      "onboarding_status": "IN_PROGRESS",
      "total_steps": 4,
      "list_of_steps": [
        { "name": "Institutional Basics", "description": "Core school info and identity",
          "status": "COMPLETED", "icon": "school", "is_enabled": true, "is_required": true },
        { "name": "Branding & Visuals", "description": "Logo and portal themes",
          "status": "PENDING", "icon": "palette", "is_enabled": true, "is_required": true },
        { "name": "Academic Structure", "description": "Grade levels and curricula",
          "status": "LOCKED", "icon": "history_edu", "is_enabled": false, "is_required": true },
        { "name": "Launch & Review", "description": "Final check & go live",
          "status": "LOCKED", "icon": "rocket_launch", "is_enabled": false, "is_required": true }
      ],
      "support_info": { "name": "VidyaPrayag Success Team", "description": "Available 9am - 6pm for setup help",
        "contact_number": "+91-9988776655", "contact_email": "support@vidyaprayag.com", "icon": "support_agent" },
      "tutorial_video_link": "https://vidyaprayag.com/tutorials/onboarding",
      "menu_features": [
        { "name": "Analytics", "is_enabled": true, "is_live": true },
        { "name": "PTM Management", "is_enabled": true, "is_live": true },
        { "name": "Scholarships", "is_enabled": false, "is_live": false }
      ],
      "app_themes": [
        { "name": "LIGHT", "is_enabled": true, "is_live": true },
        { "name": "DARK", "is_enabled": true, "is_live": true },
        { "name": "MIDNIGHT", "is_enabled": true, "is_live": true }
      ],
      "tos_link": "https://vidyaprayag.com/terms",
      "privacy_policy_link": "https://vidyaprayag.com/privacy"
    }
  }
}
```

## Step-status computation

| Step | COMPLETED when |
|---|---|
| `BASIC`     | `schools.name` AND `schools.board_affiliation` are non-blank |
| `BRANDING`  | `schools.logo_url` IS NOT NULL |
| `ACADEMIC`  | ≥ 1 row in `school_classes` for this school |
| `REVIEW`    | `schools.onboarding_status = 'COMPLETED'` |

Subsequent steps fall back to `PENDING` (one step ahead) or `LOCKED` (further
ahead). `is_enabled` mirrors "previous step is COMPLETED".

## DB tables used

| Table | Operation |
|---|---|
| `users`           | READ |
| `schools`         | READ |
| `school_classes`  | COUNT |

## Errors

| Code | Meaning |
|---|---|
| 401  | Token missing or invalid |
| 404  | User row not found |
