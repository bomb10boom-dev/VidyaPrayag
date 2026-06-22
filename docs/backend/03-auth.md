# 03 — Authentication (check / signup / login / send-otp)

**File:** `server/src/main/kotlin/com.littlebridge.enrollplus/feature/auth/AuthRouting.kt`
**Spec ref:** `vidya_prayag_api_spec.artifact.md` §Module: User Authentication
**UI consumers:**
- `composeApp/.../ui/auth/AuthBottomSheet.kt`
- `shared/.../feature/auth/data/remote/AuthApi.kt`

---

## Endpoints

| Method | Path | Auth |
|---|---|---|
| POST | `/api/v1/auth/check-user` | public |
| POST | `/api/v1/auth/signup`     | public |
| POST | `/api/v1/auth/login`      | public |
| POST | `/api/v1/auth/send-otp`   | public |

**Backward-compat aliases (do not remove yet):** the same four handlers also
respond at `/auth/check-user`, `/auth/signup`, `/auth/login`, `/auth/send-otp`
because the existing `shared/.../AuthApi.kt` still calls those paths. Delete
the legacy block at the bottom of `AuthRouting.kt` once the mobile clients
migrate.

---

## 1. POST /api/v1/auth/check-user

**Request**
```json
{ "identifier": "9876543210", "role": "PARENT" }
```
(legacy `contact` field is also accepted)

**Response 200**
```json
{
  "success": true,
  "data": {
    "is_new_user": false,
    "auth_method_required": "OTP",
    "message": "User found. Please continue with OTP."
  }
}
```

Routing logic:
- contains `@` → `auth_method_required = "PASSWORD"`
- otherwise   → `"OTP"`

---

## 2. POST /api/v1/auth/signup

**Request**
```json
{
  "name": "Arjun Sharma",
  "identifier": "9876543210",
  "role": "PARENT",
  "password": "secret",
  "otp": "123456",
  "device_info": { "device_id": "XY123", "platform": "ANDROID" }
}
```

**Response 201**
```json
{
  "success": true,
  "message": "Account created successfully",
  "data": {
    "token": "eyJhbGciOi…",
    "refresh_token": "eyJhbGciOi…",
    "user_id": "uuid-string",
    "name": "Arjun Sharma",
    "role": "PARENT",
    "profile_completed": false
  }
}
```

- Password is hashed with SHA-256 (MVP — upgrade to BCrypt for production).
- Refresh token is stored in `users.refresh_token`.
- 409 Conflict if the contact already exists.

---

## 3. POST /api/v1/auth/login

**Request**
```json
{
  "identifier": "arjun@example.com",
  "role": "PARENT",
  "password": "secret"
}
```
or for mobile:
```json
{ "identifier": "9876543210", "role": "PARENT", "otp": "123456" }
```

**Response 200** — same shape as signup, but HTTP 200.

- Email login → checks SHA-256 hash.
- Phone login → checks OTP == `"123456"` (mock).

---

## 4. POST /api/v1/auth/send-otp

Always responds 200 with `"OTP sent successfully. Use 123456 in dev."`.
Hook up a real SMS provider (Msg91 / Twilio) here in production.

---

## DB tables touched

| Table | Operation |
|---|---|
| `users` | READ on check & login, INSERT on signup, UPDATE refresh_token on signup+login |

## JWT details

See `core/JwtConfig.kt` — claims are `sub` (userId), `role`, `name`.
Verifier is automatically wired by `core/SecurityModule.configureJwt()`.

## Manual steps still needed

- Set `JWT_SECRET` env to a strong random value in production.
- Replace SHA-256 with BCrypt when you have time.
- Replace `/send-otp` mock with a real SMS provider.
