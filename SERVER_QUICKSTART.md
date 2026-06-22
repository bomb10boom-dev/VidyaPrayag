# VidyaPrayag Server — Quickstart (Windows / CMD)

> Branch: **`backend-by-abuzar`**
> Target audience: developers who just want to run the Ktor backend locally on Windows and hit every endpoint with **Postman**.

If your last `gradlew.bat :server:run` ran for **30+ minutes without finishing**, this guide is for you. It explains exactly what changed, how to update your local clone, and the fastest way to boot the API.

---

## 1. Why the build used to take 30+ minutes

Before this fix, `:server` (the JVM-only Ktor backend) depended on `:shared`, which is a **Kotlin Multiplatform** module that targets Android, iOS, JVM, JS, and wasmJs and pulls in:

- Android Gradle Plugin 9.x (beta)
- Compose Multiplatform
- Room + KSP
- Kotlin/Native toolchain (downloads ~1 GB the first time)

So every time you ran `gradlew.bat :server:run`, Gradle had to configure all of that **even though the backend never actually needed it.** On a cold clone, the first‑time download alone can take 30+ minutes on average internet.

**What we changed (in this PR):**

| Change | File |
|---|---|
| Inlined the 2 trivial symbols the server consumed from `:shared` (`SERVER_PORT`, `Greeting`) | `server/src/main/kotlin/com.littlebridge.enrollplus/ServerEntry.kt` (new) |
| Removed `implementation(projects.shared)` from the server module | `server/build.gradle.kts` |
| Added a `-Pserver-only=true` flag that makes Gradle **completely skip** including `:composeApp` and `:shared` in the build | `settings.gradle.kts` |

Net result: with `-Pserver-only=true`, Gradle no longer downloads or configures any Android / iOS / JS / wasm / Compose / Room / KSP artifact. A cold first build now just pulls JVM + Ktor + Exposed + JDBC drivers (a few hundred MB at most).

---

## 2. One-time prerequisites on Windows

| Tool | Version | How to check |
|---|---|---|
| **JDK 21** (any vendor: Temurin, Oracle, Zulu…) | `21+` | `java -version` |
| **Git** | any | `git --version` |
| **Postman** (desktop) | any recent | already downloaded per your message |

> You do **not** need to install Gradle separately — the repo ships `gradlew.bat`.

If `java -version` prints `17` or older, install JDK 21 from <https://adoptium.net/temurin/releases/?version=21> and re‑open CMD.

---

## 3. Update your local clone (you already cloned it before)

Open **Command Prompt** in the repo folder and run:

```cmd
:: 1. Make sure you're on the right branch
git checkout backend-by-abuzar

:: 2. Pull the latest fixes (this PR + the previous server-fix PR #2)
git pull origin backend-by-abuzar

:: 3. (Recommended on Windows after a long broken build) clear stale Gradle state
gradlew.bat --stop
rmdir /S /Q .gradle
rmdir /S /Q build
rmdir /S /Q server\build
```

> The `rmdir` commands wipe local build caches only. They do **not** touch your source code or your global `~/.gradle` folder. They're safe to run any time the build feels stuck.

---

## 4. Run the server — the **fast path**

```cmd
gradlew.bat :server:run -Pserver-only=true
```

What this does:

- `-Pserver-only=true` tells `settings.gradle.kts` to include **only** `:server` (skip `:composeApp` + `:shared`).
- Gradle then configures one JVM module, downloads JVM-only artifacts, compiles ~20 Kotlin files, and runs `com.littlebridge.enrollplus.ApplicationKt`.

**Expected timings on a typical laptop (8 GB RAM, average internet):**

| Phase | First time | Subsequent runs |
|---|---|---|
| Download Gradle distribution (if first ever) | 1–2 min | instant |
| Download JVM/Ktor/Exposed JARs | 2–4 min | instant (cached) |
| Compile `:server` | 30–60 s | 5–10 s (incremental) |
| **Total cold start** | **~5 min** | **~10 s** |

You should see this once it's ready:

```
... INFO  ktor.application - Application started in 1.234 seconds.
... INFO  ktor.application - Responding at http://0.0.0.0:8080
```

> Leave this CMD window open — it's your running server. To stop it, press **Ctrl+C**.

If you ever want a full multi-module build (including the mobile/web apps), just drop the flag:

```cmd
gradlew.bat :server:run
```

(That's the slow path — it will configure `:composeApp` and `:shared` again.)

---

## 5. Verify the server is alive

Open a **second** CMD window (keep the first one running) and run:

```cmd
curl http://127.0.0.1:8080/
```

Expected response:

```
Ktor: Hello, JVM! — VidyaPrayag API v1 is live
```

If you see that line, the server is healthy. Time to switch to Postman.

### 5.1 Connect a REAL PHONE to your laptop backend (same WiFi)

If you are testing the Android dev build on a physical phone and seeing errors
like `Endpoint not found: /api/v1/school/messages/threads/{id}/messages` or the
calendar `working_days` error, the phone is almost certainly hitting the stale
**Render** backend instead of your laptop. Fix it like this:

1. Find your laptop's LAN IP:
   - Windows: `ipconfig` → IPv4 Address (e.g. `192.168.1.9`)
   - macOS/Linux: `ipconfig getifaddr en0` or `hostname -I`
2. Copy `local.properties.example` → `local.properties` and set:
   ```properties
   devBaseUrl=http://192.168.1.9:8080
   ```
3. Rebuild/reinstall the **dev** flavor app.
4. On the phone, look at the small banner at the top of the app:
   - **Green** `DEV → http://192.168.1.9:8080` = good, hitting your laptop.
   - **Red** `DEV → https://vidyaprayag-1.onrender.com ⚠ set devBaseUrl` = still
     on Render; `devBaseUrl` wasn't picked up (rebuild after editing).
5. Prove it from the phone browser — open:
   ```
   http://192.168.1.9:8080/api/v1/config/version
   ```
   The returned `git_sha` must match your laptop `git rev-parse --short HEAD`.
   If the page won't load at all, your laptop firewall is blocking port 8080 on
   the LAN — allow it (Windows Defender Firewall → inbound rule for 8080).

> The server already binds `0.0.0.0:8080`, so once the firewall allows it and
> `devBaseUrl` is set, the phone reaches the laptop and both the messages
> conversation endpoint and the fixed calendar response work as expected.

---

## 6. Test all APIs in Postman (locally installed)

The repo already ships a Postman collection + environment. Follow these steps:

### 6.1 Import the collection and environment

1. Open Postman (desktop).
2. Click **Import** (top-left).
3. Drag-and-drop these **two** files from your repo into the import dialog:
   - `docs/backend/postman/VidyaPrayag.postman_collection.json`
   - `docs/backend/postman/VidyaPrayag.local.postman_environment.json`
4. Click **Import**.

You should now see:
- A collection named **VidyaPrayag** in the left sidebar.
- An environment named **VidyaPrayag • Local** in the top-right environment dropdown.

### 6.2 Select the environment

In the top-right environment selector, choose **`VidyaPrayag • Local`**.

It is pre-configured with:

| Variable | Value | Notes |
|---|---|---|
| `baseUrl` | `http://127.0.0.1:8080` | Matches the server's default port |
| `accessToken` | *(empty initially)* | Auto-filled by the **Login** request |
| `phone` | `+919876543210` | A demo user that is auto-seeded on first boot |

### 6.3 Run the public endpoints (no token needed)

Open these requests in the collection and click **Send** for each:

| # | Request | Expected |
|---|---|---|
| 1 | `GET {{baseUrl}}/api/v1/content/landing` | `200` with `success: true` and the marketing copy |
| 2 | `GET {{baseUrl}}/api/v1/config/app-status` | `200` with `version_check` payload |
| 2b | `GET {{baseUrl}}/api/v1/config/version` | `200` with `git_sha`/`build_time`/`requested_host` — use this to confirm WHICH backend answered |
| 3 | `POST {{baseUrl}}/api/v1/auth/check-user` body `{"identifier":"{{phone}}"}` | `200` with `exists: true` |
| 4 | `POST {{baseUrl}}/api/v1/auth/send-otp` body `{"phone":"{{phone}}"}` | `200`, `Use 123456 in dev.` |

### 6.4 Log in and capture the token

| # | Request | What it does |
|---|---|---|
| 5 | `POST {{baseUrl}}/api/v1/auth/login` | The request has a **Tests** script that stores `data.token` into the `accessToken` environment variable. |

After sending **Login** you should see at the bottom of Postman:

```
accessToken set to eyJhbGciOi...
```

> If the collection request body uses `phone`/`otp`, the API expects those exact field names. Use OTP **`123456`** (the dev placeholder, baked into the seed).

### 6.5 Hit the protected endpoints

These all use the `Authorization: Bearer {{accessToken}}` header (already pre-set in the collection):

| # | Request | Expected |
|---|---|---|
| 6 | `GET {{baseUrl}}/api/v1/user/details` | `200` with full profile JSON |
| 7 | `GET {{baseUrl}}/api/v1/user/profile` | `200` |
| 8 | `GET {{baseUrl}}/api/v1/schools` | `200` with seeded school list |
| 9 | `GET {{baseUrl}}/api/v1/announcements` | `200` |
| 10 | `POST {{baseUrl}}/api/v1/admissions/enquiry` | `200` / `201` |
| 11 | `POST {{baseUrl}}/api/v1/onboarding/...` (see collection) | `200` |

### 6.6 Sanity-check the auth guard

In the **`/api/v1/user/details (no token)`** request, the `Authorization` header is left blank. Sending it should return:

```json
HTTP 401
{
  "success": false,
  "message": "Session expired, please login again",
  "errorCode": "UNAUTHORIZED"
}
```

If you get that envelope, JWT auth + the global error handler are working correctly.

> A full per-endpoint walkthrough already lives in **`docs/backend/POSTMAN_TESTING_GUIDE.md`** — refer to that file for request bodies and assertions.

---

## 7. Common issues and how to fix them

| Symptom | Cause | Fix |
|---|---|---|
| `gradlew.bat` runs for 30+ minutes and never finishes | You forgot `-Pserver-only=true`, so Gradle is configuring `:shared` (KMP) and downloading Android/iOS toolchain | Stop with Ctrl+C, run `gradlew.bat --stop`, then re-run with the flag |
| `Port 8080 already in use` | Another process is bound | Either kill it, or set `set PORT=8088` in CMD before `gradlew.bat :server:run -Pserver-only=true` |
| `JAVA_HOME is not set` | JDK not on PATH | Install JDK 21, set `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21...` |
| `Could not resolve …` while downloading deps | Spotty internet / corp firewall | Re-run; Gradle resumes downloads |
| Postman `Could not get any response` | Server not running, or different port | Check the first CMD window still shows `Responding at http://0.0.0.0:8080` |
| `401 UNAUTHORIZED` on every protected request | `accessToken` env variable is empty | Re-run the **Login** request; check the Tests tab actually stored the token |

---

## 8. What changes when this PR is merged

Nothing for the mobile/web team — `:composeApp` and `:shared` still build exactly the same way when you don't pass `-Pserver-only=true`. The flag is **purely opt-in**.

For backend developers and CI:

- Faster local iteration (`:server` boots in ~10 s warm).
- Smaller container images (no need to ship `:shared`).
- Easier cloud deploys — `:server` is now a self-contained JVM module.

---

## 9. Quick command cheatsheet

```cmd
:: First time only — pull this branch
git checkout backend-by-abuzar
git pull origin backend-by-abuzar

:: Fast run (use this 99% of the time)
gradlew.bat :server:run -Pserver-only=true

:: Stop everything Gradle has cached in memory
gradlew.bat --stop

:: Hard reset local build state (safe, keeps source files)
rmdir /S /Q .gradle build server\build
```

Happy testing.
