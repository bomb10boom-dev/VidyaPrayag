# 02 — App Status / Splash Handshake

**File:** `server/src/main/kotlin/com.littlebridge.enrollplus/feature/config/AppStatusRouting.kt`
**Spec ref:** `vidya_prayag_api_spec.artifact.md` §Screen: Splash / Startup (Global)

---

## Endpoint

```
GET /api/v1/config/app-status
```

- **Auth:** Public (handshake API)
- **Headers (per spec):** `App-Version`, `Platform`, `Device-Id` — currently
  **not enforced** server-side; treat as TODO once the mobile client always
  sends them.

## Response (200 OK)

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
    "maintenance": {
      "is_under_maintenance": false,
      "estimated_end_time": "2024-10-24T10:00:00Z",
      "message": "We're upgrading our servers. We'll be back shortly."
    },
    "flags": {
      "is_whatsapp_sync_enabled": true,
      "show_scholarships": true,
      "is_ai_narrative_live": true,
      "theme_mode_override": "SYSTEM",
      "support_contact": "+91-9876543210"
    }
  }
}
```

## DB tables used

| Table | Operation |
|---|---|
| `app_config` | READ (3 rows: `version_check`, `maintenance`, `flags`) |

## How to flip a feature flag without a deploy

```sql
UPDATE app_config
SET value = '{ "is_whatsapp_sync_enabled": false,
               "show_scholarships": true,
               "is_ai_narrative_live": true,
               "theme_mode_override": "SYSTEM",
               "support_contact": "+91-9876543210" }'
WHERE key = 'flags';
```

To force-update everyone below 2.5.0:
```sql
UPDATE app_config
SET value = '{ "current_version": "2.5.0", "minimum_required_version": "2.5.0",
               "force_update": true, "update_url": "…", "update_message": "Critical update" }'
WHERE key = 'version_check';
```

## cURL test

```bash
curl http://localhost:8080/api/v1/config/app-status | jq
```
