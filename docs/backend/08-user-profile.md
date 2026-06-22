# 08 — User / School Profile (Philosophy, Tour Videos, Gallery)

**File:** `server/src/main/kotlin/com.littlebridge.enrollplus/feature/user/UserProfileRouting.kt`
**Spec ref:** `vidya_prayag_api_spec2.artifact.md` §Screen: User Profile / School Profile
**UI consumer:** `composeApp/.../ui/screens/admin/InstitutionalProfileScreen.kt`

---

## Endpoints

```
GET /api/v1/user/profile
PUT /api/v1/user/profile/philosophy
PUT /api/v1/user/profile/tour-videos
PUT /api/v1/user/profile/gallery
```
All require JWT.

---

## 1. GET /profile

```json
{
  "success": true,
  "message": "Profile fetched successfully",
  "data": {
    "public_profile": true,
    "philosophy_details": {
      "core_mission": "Empowering young minds through holistic education.",
      "learning_model": "Inquiry-based collaborative learning",
      "primary_language": "English"
    },
    "video_tour_data": [
      "https://vidyaprayag.com/videos/tour1.mp4",
      "https://vidyaprayag.com/videos/lab_intro.mp4"
    ],
    "gallery": {
      "images": [ "https://…/img1.jpg", "https://…/img2.jpg" ],
      "total_storage": "10 GB",
      "storage_used": "2.4 GB"
    }
  }
}
```

## 2. PUT /philosophy

```json
{
  "core_mission": "Updated mission statement.",
  "learning_model": "Project-based learning",
  "primary_language": "Hindi/English"
}
```
Response: `200 { "success": true, "message": "Philosophy updated successfully" }`.

All three fields are optional — only provided ones overwrite the existing
values. The row is auto-created on first PUT if it doesn't exist.

## 3. PUT /tour-videos

```json
{ "video_tour_data": [ "https://…/tour1.mp4", "https://…/tour2.mp4" ] }
```
Sync-replace semantics: all existing video rows for the school are deleted
and re-inserted in the order of the payload.

## 4. PUT /gallery

```json
{ "images": [ "https://…/img1.jpg", "https://…/new_upload.jpg" ] }
```
Same sync-replace semantics. Returns 200 with updated storage block:
```json
{
  "success": true,
  "message": "Gallery updated successfully",
  "data": { "storage_used": "2.6 GB", "total_storage": "10 GB" }
}
```

> ⚠️ `storage_used` is currently **approximated** as `images.size × 0.2 GB`.
> Once raw uploads are wired in, replace with actual file-size sum.

## DB tables touched

| Table | Operation |
|---|---|
| `school_philosophy` | INSERT (lazy) / UPDATE |
| `school_media`      | DELETE then INSERT (per kind: VIDEO or IMAGE) |
| `storage_metrics`   | INSERT (lazy) / UPDATE storage_used |

## User responsibilities (manual)

1. **Decide on a file-storage provider** (S3 / Cloudinary / Supabase Storage)
   if you want real binary uploads. Currently the API expects URLs only.
2. When you do wire uploads, add a new `POST /api/v1/uploads` endpoint that
   returns a public URL, and have the UI insert that URL into the PUT body
   here.
