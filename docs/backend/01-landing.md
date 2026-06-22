# 01 — Landing Page Content

**File:** `server/src/main/kotlin/com.littlebridge.enrollplus/feature/content/LandingRouting.kt`
**Spec ref:** `vidya_prayag_api_spec.artifact.md` §Screen: Common Landing Page
**UI screen consumer:** `composeApp/src/commonMain/kotlin/com.littlebridge.enrollplus/presentation/landing/CommonLandingScreen.kt`

---

## Endpoint

```
GET /api/v1/content/landing
```

- **Auth:** Public (no JWT)
- **Caching:** spec asks for 24h Redis TTL — currently served fresh from DB.
  Swap in Redis later by wrapping the `dbQuery { … }` in a cache layer.

## Response (200 OK)

```json
{
  "success": true,
  "message": "Landing page content fetched successfully",
  "data": {
    "top_tagline": "Education with Trust.",
    "sub_tagline": "Progress with Purpose.",
    "parent_info": { "top_tagline": "FOR PARENTS", "sub_tagline": "…",
      "list_of_features": [...], "list_of_sub_features": [...] },
    "school_info": { … },
    "list_of_offerings": [ { "icon_url": "…", "heading": "…",
      "description": "…", "is_live": true } ],
    "list_of_portals": [ … ],
    "login_modes": ["EMAIL","MOBILE","GOOGLE","APPLE"],
    "tos_link": "https://vidyaprayag.com/terms",
    "privacy_policy_link": "https://vidyaprayag.com/privacy"
  }
}
```

## DB tables used

| Table | Operation |
|---|---|
| `cms_landing_content` | READ (full scan, <20 rows) |

## How the data is stored

`cms_landing_content` is a small KV table (`key VARCHAR PRIMARY KEY`,
`value TEXT`). Object/array fields like `parent_info` or `list_of_offerings`
are stored as raw JSON in `value` and parsed in the route handler. This keeps
the schema simple and CMS-friendly — you can edit a single row in the DB to
push a new tagline live.

## How to edit the content

```sql
UPDATE cms_landing_content SET value = 'Education with Joy.' WHERE key = 'top_tagline';
UPDATE cms_landing_content SET value = '[{"icon_url":"…","heading":"…","description":"…","is_live":true}]' WHERE key = 'list_of_offerings';
```

To re-seed from scratch (DESTRUCTIVE):
```sql
DELETE FROM cms_landing_content;
-- restart server → Seed.populateIfEmpty() refills it
```

## cURL test

```bash
curl http://localhost:8080/api/v1/content/landing | jq
```
