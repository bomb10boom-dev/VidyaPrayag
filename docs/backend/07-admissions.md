# 07 — Admission Enquiries

**File:** `server/src/main/kotlin/com.littlebridge.enrollplus/feature/admissions/AdmissionRouting.kt`
**Spec ref:** `vidya_prayag_api_spec2.artifact.md` §Screen: Admission Enquiries Dashboard
**UI consumer:** `composeApp/.../ui/screens/admin/AdmissionCRMDashboard.kt`

---

## Endpoints

```
GET /api/v1/admissions/enquiries/summary
GET /api/v1/admissions/enquiries?page=1&limit=20
```
Both require JWT (ADMIN/STAFF role conceptually — role-check not enforced yet).

---

## 1. Summary

```json
{
  "success": true,
  "message": "Enquiry summary fetched successfully",
  "data": {
    "summary_count": { "total": 5, "new": 2, "follow_ups": 2, "converted": 1 },
    "recent_enquiries": [
      { "student_name": "Aarav Sharma", "parent_name": "Rajesh Sharma",
        "class": "Grade 5", "date": "2024-05-18", "status": "new",
        "profile_pic": "https://…/student1.jpg" }
    ],
    "efficiency": "33%"
  }
}
```

**Efficiency formula:**
```
efficiency = converted / (converted + follow_ups)   (as percentage, 0 decimals)
```
Returns `"0%"` when the denominator is 0.

**Recent enquiries:** newest 5 rows by `date DESC`.

---

## 2. List

`GET /api/v1/admissions/enquiries?page=1&limit=20`

```json
{
  "success": true,
  "data": {
    "enquiries": [ … same row shape as recent_enquiries above … ],
    "pagination": {
      "current_page": 1,
      "total_pages": 1,
      "total_records": 5
    }
  }
}
```

Bounds: `1 ≤ page`, `1 ≤ limit ≤ 100`.

## DB tables touched

| Table | Operation |
|---|---|
| `admission_enquiries` | READ (full-scan + count + paginated select) |

## Errors

| Code | Meaning |
|---|---|
| 401  | JWT missing/invalid |
| 404  | User not associated with any school |
