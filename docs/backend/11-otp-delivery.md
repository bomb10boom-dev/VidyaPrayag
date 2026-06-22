# 11 — OTP Delivery (multi-provider, free-tier friendly)

**Files:**
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/auth/OtpService.kt`
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/auth/OtpDeliveryProvider.kt` (thin facade)
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/auth/delivery/**`
- `server/src/main/kotlin/com.littlebridge.enrollplus/feature/auth/OtpAdminRouting.kt`
- SQL patch: `docs/backend/sql/01_supplementary_schema.sql` § SECTION 2b

---

## TL;DR

OTPs are now delivered by a **provider chain**. The dispatcher tries vendors in
order, stopping at the first success. Every attempt is recorded in
`otp_delivery_attempts` so support can answer *"why didn't this user get their
OTP?"* without log-trawling.

Default chain (when env says nothing):
```
SMS lane:        Fast2SMS  →  MSG91  →  Twilio
WhatsApp lane:   WhatsApp Cloud  →  Twilio(WA)
Email lane:      SMTP
Always-last:     Console (stdout, dev only)
```

Set **zero** env vars → the chain falls all the way to `ConsoleProvider` and
prints to stdout (same as the old mock behaviour). Set one provider's creds →
the chain uses that. Set many → cheapest first.

---

## Architecture diagram

```
            ┌──────────────────┐
HTTP req →  │ AuthRouting      │  /api/v1/auth/send-otp
            │   /send-otp      │
            └────────┬─────────┘
                     │ identifier, purpose, ip, ua, device_id, locale
                     ▼
            ┌──────────────────┐
            │ OtpService.send  │  ← 6-digit code, SHA-256(code+salt+pepper)
            │  • upsert auth_otps
            │  • rate-limit
            └────────┬─────────┘
                     │ DeliveryOutcome
                     ▼
            ┌──────────────────────┐
            │ OtpDeliveryProvider  │  (thin facade)
            └────────┬─────────────┘
                     │
                     ▼
            ┌──────────────────────┐
            │ OtpDeliveryDispatcher│
            │  • resolve chain     │   ← OTP_PROVIDER / OTP_PROVIDER_ORDER /
            │  • channel guard     │     OTP_CHANNEL_ORDER
            │  • first Sent wins   │
            └────────┬─────────────┘
                     │ for each provider:
        ┌────────────┴────────────────────────────────┐
        ▼                                             ▼
  isConfigured()? — short-circuits unconfigured     send()
        ▼                                             ▼
                                              Sent | Failed | Skipped
                                                       │
                                                       ▼
                                          OtpDeliveryAttemptsTable
                                            (one row per attempt)
```

---

## Providers shipped

| name | channel | typical cost | configured by env |
|---|---|---|---|
| `whatsapp_cloud` | whatsapp | **free** ≤ 1000 conversations/mo | `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID` |
| `fast2sms` | sms | ~₹0.15–0.25/SMS (India) | `FAST2SMS_API_KEY` (+ optional DLT fields) |
| `msg91` | sms | ~₹0.18–0.22/SMS (India, DLT) | `MSG91_AUTH_KEY`, `MSG91_FLOW_ID` |
| `twilio` | sms or whatsapp | ~₹0.40+/SMS (intl) | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` |
| `smtp` | email | **free** (Gmail / Resend / SES / Brevo) | `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` |
| `console` | console | free | `OTP_ENABLE_CONSOLE_FALLBACK=true` (default) |

Adding another provider is a single file under `feature/auth/delivery/providers/`
+ one entry in `OtpDeliveryDispatcher.knownProviders`. No other code changes.

---

## Chain resolution rules

The dispatcher walks these env vars in order — first non-empty wins:

1. **`OTP_PROVIDER`** — hard pin, single provider. Use during an incident to
   force traffic onto a specific gateway. Set to `auto`, `chain`, `default`,
   or leave blank to disable.
2. **`OTP_PROVIDER_ORDER`** — CSV of provider names. Use to opinionatedly
   pick the chain order, e.g. `whatsapp_cloud,fast2sms,smtp`.
3. **`OTP_CHANNEL_ORDER`** — CSV of channels. Default. Within each channel
   we run **all** configured providers for that channel. e.g.
   `sms,whatsapp,email` will try Fast2SMS then MSG91 then Twilio (all SMS),
   then WhatsApp Cloud (WA), then SMTP (email).

After all of the above, `ConsoleProvider` is appended last when
`OTP_ENABLE_CONSOLE_FALLBACK=true`.

Providers whose `channel` is incompatible with the identifier type are pruned
before the chain runs (e.g. SMTP is skipped silently for `+919876543210`).

---

## DB schema

### `auth_otps` (unchanged columns, but newly populated)

The columns `delivery_channel`, `delivery_provider`, `provider_message_id`
existed already but were never written. Now `OtpService` updates them with
the **winning** provider's metadata after a successful send.

### `otp_delivery_attempts` (NEW)

Full audit trail. One row per provider attempt during a single `/send-otp`.

| column | type | notes |
|---|---|---|
| `id` | uuid pk |  |
| `otp_id` | uuid, nullable | soft-link to `auth_otps.id` |
| `identifier` | text | snapshot for forensics |
| `purpose` | text |  |
| `attempt_index` | int | 0 = first provider tried |
| `provider_name` | text |  |
| `channel` | text | sms/whatsapp/email/voice/console |
| `status` | text | sent/failed/skipped |
| `provider_message_id` | text | vendor-side reference |
| `http_status` | int |  |
| `latency_ms` | int |  |
| `reason` | text |  |
| `raw_response` | text | trimmed to 240 chars |
| `created_at` | timestamptz |  |

Retention: 7 days via `purge_old_delivery_attempts()` (pg_cron or in-line).

---

## Endpoints

### Public auth (unchanged from before)

| Method | Path | Behaviour change |
|---|---|---|
| POST | `/api/v1/auth/send-otp` | Now reads `Accept-Language` header → forwarded to provider as `locale`. Response unchanged. |
| POST | `/api/v1/auth/verify-otp` | Unchanged. |

### Ops-only admin (NEW)

All three are **404'd** when `OTP_ADMIN_TOKEN` is empty (no advertising).
When set, they require header `X-Admin-Token: <token>`. Constant-time compare.

```bash
# Snapshot of provider config + chain order
GET /api/v1/admin/otp/diagnostic
→ { providers: [...], channel_order_default, provider_order_override,
    pinned_provider, console_fallback_enabled, dev_return_code }

# Last N audit rows for one identifier
GET /api/v1/admin/otp/attempts?identifier=+91...&limit=50
→ { identifier, count, attempts: [...] }

# Force a real provider chain run (does NOT touch auth_otps)
POST /api/v1/admin/otp/test-send  { "identifier": "+91...", "locale": "hi" }
→ { ok, winning_provider, winning_channel, provider_message_id,
    failure_reason, code, attempts: [...] }
```

---

## Render free-tier deployment notes

This stack is **specifically tuned** for Render's 512 MB free tier:

| concern | mitigation |
|---|---|
| Cold dyno → slow first request | bounded provider timeouts (5s connect / 8s req / 15s socket) so a hung provider doesn't pin the dyno |
| Memory pressure | Ktor CIO client is a single shared instance; jakarta.mail loads lazily on first email |
| No Redis | OTP state lives in Supabase Postgres (`auth_otps`). No second store. |
| No sidecar containers | All providers are in-process via HTTP/SMTP. Zero extra services. |
| 100 GB/month egress | Each OTP is ~1 KB outbound. We're nowhere near the cap. |
| Free Postgres = 500 MB | `otp_delivery_attempts` purged after 7 days; main `auth_otps` purged after expiry. |

**Recommended Render env for v1 production**:

```
OTP_PEPPER=<openssl rand -hex 32>
OTP_DEV_RETURN_CODE=false
OTP_ENABLE_CONSOLE_FALLBACK=false
OTP_ADMIN_TOKEN=<openssl rand -hex 32>
OTP_CHANNEL_ORDER=sms,whatsapp,email
# + one or more provider creds (Fast2SMS recommended for India)
FAST2SMS_API_KEY=<your key>
# Optional second lane for free WhatsApp delivery:
WHATSAPP_ACCESS_TOKEN=<meta token>
WHATSAPP_PHONE_NUMBER_ID=<id>
# Optional email lane:
SMTP_HOST=smtp.resend.com
SMTP_PORT=465
SMTP_USERNAME=resend
SMTP_PASSWORD=<resend api key>
SMTP_FROM=VidyaPrayag <noreply@yourdomain.com>
```

---

## Manual steps after merging this PR

1. **Run the SQL patch** in Supabase Dashboard → SQL Editor:
   ```
   docs/backend/sql/01_supplementary_schema.sql
   ```
   It's idempotent (`CREATE TABLE IF NOT EXISTS`). Safe to re-run.

2. **Pick a provider** and add its creds to Render env:
   - **Cheapest India-only path**: sign up for [Fast2SMS](https://www.fast2sms.com),
     grab the API key, set `FAST2SMS_API_KEY`. Free wallet credit covers
     smoke testing.
   - **Truly free for the first 1000/mo**: WhatsApp Cloud API via Meta.
     Requires a Meta dev account, app, and an approved authentication
     template. Best UX (one-tap OTP autofill on Android).
   - **Email-only signup**: any SMTP server. Resend has 3000 emails/mo free.

3. **Set `OTP_ADMIN_TOKEN`** to enable the diagnostic endpoints.

4. **Verify**: hit `GET /api/v1/admin/otp/diagnostic` — should list which
   providers are `configured: true`.

5. **Smoke-test**: `POST /api/v1/admin/otp/test-send` with your own phone
   number. Response shows the winning provider + the actual OTP code.

6. **Flip dev flags off**:
   ```
   OTP_DEV_RETURN_CODE=false
   OTP_ENABLE_CONSOLE_FALLBACK=false
   ```

7. **(Optional)** Enable `pg_cron` in Supabase and uncomment the two
   `cron.schedule(...)` lines at the bottom of `01_supplementary_schema.sql`
   for fully automatic cleanup.

---

## Wire-format compatibility

`/api/v1/auth/send-otp` and `/api/v1/auth/verify-otp` request/response shapes
are **unchanged**. The mobile clients in `composeApp/` and `shared/` keep
working without code changes.

The only new client-facing capability is the **`Accept-Language`** header —
when present and set to `hi` / `mr`, the OTP body is localized. English is
the default. The wire DTOs do not expose this header explicitly; the existing
Ktor `HttpClient` configurations in `:shared` will forward it if you set it,
or omit it if you don't.
