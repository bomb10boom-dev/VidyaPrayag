# 06 — School Announcements

**File:** `server/src/main/kotlin/com.littlebridge.enrollplus/feature/announcements/AnnouncementRouting.kt`
**Spec ref:** `vidya_prayag_api_spec2.artifact.md` §Screen: School Dashboard (Announcement Tab)
**UI consumers:**
- `composeApp/.../ui/screens/admin/SchoolAnnouncementsScreen.kt`
- `composeApp/.../ui/screens/parent/ParentAnnouncementScreen.kt`

---

## Endpoints

```
GET  /api/v1/school/announcements?user_id=…
GET  /api/v1/school/announcements/search?query=vacation&user_id=…
POST /api/v1/school/announcements/sync-whatsapp
```
All require JWT.

---

## 1. List

```json
{
  "success": true,
  "message": "Announcements fetched successfully",
  "data": {
    "announcements": [
      { "type": "Holidays", "event_id": "EVT_101", "title": "Summer Vacation",
        "sub_title": "Starting from 1st June",
        "description": "The school will remain closed …",
        "event_image": "https://…/summer_vacation.png",
        "date": "2024-06-01" }
    ]
  }
}
```

Ordering: by `date` DESC.

## 2. Search

`GET /api/v1/school/announcements/search?query=vacation`

Case-insensitive LIKE on `title` OR `description`.

## 3. Sync to WhatsApp

```json
POST /api/v1/school/announcements/sync-whatsapp
{
  "school_id": 1,
  "announcement_ids": ["EVT_101","EVT_102"]
}
```
- If `school_id` is omitted, the user's own school (resolved from JWT) is used.
- If `announcement_ids` is empty/missing, all unsynced announcements are picked.

**Response 202**
```json
{
  "success": true,
  "message": "Sync process initiated successfully",
  "data": {
    "job_id": "SYNC_WA_AB12CD34",
    "total_queued": 150,
    "estimated_time_minutes": 5
  }
}
```

> ⚠️ This implementation is a **mock**. It marks announcements as `synced_to_wa = true`
> and inserts one row per (announcement × parent) into `whatsapp_logs` with
> status = `QUEUED`. No real WhatsApp call is made. To go live, replace the
> insert loop with a call to your Twilio / Meta WhatsApp Business API. Search
> for the `TODO` comment in the file.

## DB tables touched

| Table | Operation |
|---|---|
| `announcements`  | READ (list/search), UPDATE syncedToWa flag on sync |
| `users`          | READ parent phone numbers on sync |
| `whatsapp_logs`  | INSERT job rows on sync |

## Errors

| Code | Meaning |
|---|---|
| 401 | JWT missing/invalid |
| 404 | User not associated with any school |
| 400 | `query` missing on /search |

## User responsibilities (manual)

1. Provision a WhatsApp Business API key (Twilio/Meta) and replace the mock.
2. Decide on a message-queue (Redis/RabbitMQ) for fan-out at scale; the
   `whatsapp_logs` table is shaped to be consumed by such a worker.
