# VidyaPrayag Professional API Specification

This document serves as the formal technical contract for the VidyaPrayag ecosystem. All endpoints follow RESTful principles and utilize JSON for data exchange.

---

## Screen: Common Landing Page

### API Name
Get Landing Page Content

### Purpose
Fetches the static and dynamic content for the main landing page, including taglines, parent/school information, institutional offerings, and portal listings.

### Used when:
- App launches (initial screen)
- User navigates back to landing page
- Data refresh is triggered

### Endpoint
`GET /api/v1/content/landing`

### Base URL
`https://api.vidyaprayag.com`

### Full URL
`https://api.vidyaprayag.com/api/v1/content/landing`

### Authentication
**Not Required** (Public API)

### Headers
| Key | Type | Required | Description |
|---|---|---|---|
| Content-Type | String | Yes | `application/json` |
| App-Version | String | Yes | Current app version for content compatibility |
| Platform | String | Yes | `android` / `ios` |
| Accept-Language | String | No | `en` / `hi` (defaults to `en`) |

### Request Body Schema
**No Request Body Required** (GET request)

---

### Success Response
#### Response Code
`200 OK`

#### Response JSON
```json
{
  "success": true,
  "message": "Landing page content fetched successfully",
  "data": {
    "top_tagline": "Education with Trust.",
    "sub_tagline": "Progress with Purpose.",
    "parent_info": {
      "top_tagline": "FOR PARENTS",
      "sub_tagline": "Find the perfect school for your child's unique journey",
      "list_of_features": ["Data-driven insights", "Verified institutional profiles"],
      "list_of_sub_features": ["Match matching score", "Direct inquiry"]
    },
    "school_info": {
      "top_tagline": "FOR SCHOOLS",
      "sub_tagline": "Scale excellence with intelligence.",
      "list_of_features": ["Institutional management tools", "Growth tracking"],
      "list_of_sub_features": ["Predictive analysis", "Automated workflows"]
    },
    "list_of_offerings": [
      {
        "icon_url": "https://cdn.vidyaprayag.com/icons/intel.png",
        "heading": "Next-Gen Intelligence",
        "description": "Proprietary systems powering the ecosystem.",
        "is_live": true
      }
    ],
    "list_of_portals": [
      {
        "icon_url": "https://cdn.vidyaprayag.com/icons/parent.png",
        "heading": "Parent Portal",
        "description": "Monitor your child's holistic growth.",
        "is_live": true
      },
      {
        "icon_url": "https://cdn.vidyaprayag.com/icons/admin.png",
        "heading": "Admin Portal",
        "description": "Manage institutional performance and analytics.",
        "is_live": true
      }
    ],
    "login_modes": ["EMAIL", "MOBILE", "GOOGLE", "APPLE"],
    "tos_link": "https://vidyaprayag.com/terms",
    "privacy_policy_link": "https://vidyaprayag.com/privacy"
  }
}
```

### Response Field Definitions
| Key | Type | Nullable | Description |
|---|---|---|---|
| success | Boolean | No | API status indicator |
| message | String | No | User readable status message |
| data | Object | No | Container for landing content |
| top_tagline | String | No | Primary hero section title |
| sub_tagline | String | No | Secondary hero section subtitle |
| parent_info | Object | No | Content block specifically for Parent users |
| school_info | Object | No | Content block specifically for School administrators |
| list_of_features | Array[String] | No | Primary benefit bullets for the section |
| list_of_sub_features | Array[String] | No | Secondary benefit bullets for the section |
| list_of_offerings | Array[Object] | No | Branded services provided by VidyaPrayag |
| list_of_portals | Array[Object] | No | Entry points to role-specific ecosystems |
| icon_url | String | No | Fully qualified URL to the vector or image asset |
| heading | String | No | Feature or Portal title |
| description | String | No | Short explanatory text |
| is_live | Boolean | No | Determines if the item is interactive or a placeholder |
| login_modes | Array[Enum] | No | Allowed methods: `EMAIL`, `MOBILE`, `GOOGLE`, `APPLE` |
| tos_link | String | No | Direct URL to Terms of Service |
| privacy_policy_link | String | No | Direct URL to Privacy Policy |

---

### Failure Responses
#### Internal Server Error
```json
{
  "success": false,
  "message": "Unable to fetch content. Please try again later."
}
```
**HTTP:** `500 Internal Server Error`

---

### Database Tables Involved
| Table Name | Operation |
|---|---|
| cms_landing_content | Read |
| offerings_registry | Read |
| portal_registry | Read |

### Dependencies
| Dependency | Description |
|---|---|
| CMS Service | Must be available to provide localized strings |
| CDN | Assets (icons) must be reachable |

### Used In Screens
| Screen |
|---|
| Common Landing Screen |

### Caching Strategy
| Type | Details |
|---|---|
| Local Cache | Store in local DB/Preferences for offline launch |
| Server-Side Cache | Redis (TTL: 24 Hours) |
| Expiry | Data remains valid for 24 hours unless manually cleared |

### Analytics Events
| Event |
|---|
| `landing_page_viewed` |
| `portal_click_attempt` |

### Retry Behaviour
| Scenario | Behaviour |
|---|---|
| Network Timeout | Retry 3 times with exponential backoff |
| 500 Error | Show global "Retry" CTA on screen |

---

## Screen: Splash / Startup (Global)

### API Name
Get App Status & Version Config

### Purpose
Acts as the primary "Handshake" API. It validates the client version, checks for system-wide maintenance, and provides non-harmful configuration flags to modify app behavior dynamically.

### Used when:
- App first boots (Splash Screen)
- App returns from background (optional check)
- App session starts

### Endpoint
`GET /api/v1/config/app-status`

### Base URL
`https://api.vidyaprayag.com`

### Full URL
`https://api.vidyaprayag.com/api/v1/config/app-status`

### Authentication
**Not Required** (Public Handshake)

### Headers
| Key | Type | Required | Description |
|---|---|---|---|
| Content-Type | String | Yes | `application/json` |
| App-Version | String | Yes | Current version from `BuildConfig` (e.g., `2.4.0`) |
| Platform | String | Yes | `android` / `ios` |
| Device-Id | String | Yes | Unique ID for targeted updates/flags |

### Request Body Schema
**No Request Body Required** (GET request)

---

### Success Response
#### Response Code
`200 OK`

#### Response JSON
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

### Response Field Definitions
| Key | Type | Nullable | Description |
|---|---|---|---|
| version_check | Object | No | Container for app versioning logic |
| minimum_required_version | String | No | Versions below this will trigger a Force Update block |
| force_update | Boolean | No | If true, user cannot use the app until updated |
| update_url | String | No | Direct link to Store for updates |
| maintenance | Object | No | System health status |
| is_under_maintenance | Boolean | No | If true, redirects all users to a Maintenance Screen |
| flags | Object | No | Key-Value pairs for feature toggles (Non-harmful) |
| is_whatsapp_sync_enabled | Boolean | No | Toggles WhatsApp integration features globally |
| show_scholarships | Boolean | No | Controls visibility of the Scholarships module |
| theme_mode_override | Enum | No | `SYSTEM`, `LIGHT`, `DARK`, `MIDNIGHT` |

---

### Failure Responses
#### Forbidden (Suspicious Device)
```json
{
  "success": false,
  "message": "Access denied. Device not recognized.",
  "error_code": "DEVICE_BLOCKED"
}
```
**HTTP:** `403 Forbidden`

---

### Database Tables Involved
| Table Name | Operation |
|---|---|
| app_version_registry | Read |
| feature_flags | Read |
| maintenance_schedule | Read |

### Dependencies
| Dependency | Description |
|---|---|
| Internal Config Service | Must provide the latest feature toggle states |
| Play/App Store | URLs must be valid and active |

### Used In Screens
| Screen |
|---|
| Splash Screen |
| Maintenance Overlay |
| Global App Context |

### Caching Strategy
| Type | Details |
|---|---|
| Local Cache | Do not cache (Must be fresh on every launch) |
| Server-Side Cache | CDN Cache / Redis (TTL: 5 Minutes) |

### Analytics Events
| Event |
|---|
| `app_boot_check_started` |
| `force_update_triggered` |
| `maintenance_blocked_viewed` |

### Retry Behaviour
| Scenario | Behaviour |
|---|---|
| DNS Failure | Retry 5 times (Handshake is critical) |
| Timeout | Show "System Unreachable" modal with Retry button |

---

## Module: User Authentication (Login & Signup)

### Screen: Auth Bottom Sheet
#### API Name: Check User Existence
**Purpose**: Validates if a user with the given identifier and role already exists to determine whether to show the Login or Signup flow.
**Used when**: User clicks "Continue" after entering their email or mobile number.
**Endpoint**: `POST /api/v1/auth/check-user`
**Authentication**: Not Required

**Request JSON**:
```json
{
  "identifier": "9876543210",
  "role": "PARENT"
}
```

**Request Field Definitions**:
| Key | Type | Required | Description |
|---|---|---|---|
| identifier | String | Yes | Email address or Mobile number |
| role | Enum | Yes | `PARENT`, `ADMIN`, `TEACHER` |

**Success Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "is_new_user": false,
    "auth_method_required": "PASSWORD",
    "message": "User found. Please enter your password."
  }
}
```

**Response Field Definitions**:
| Key | Type | Nullable | Description |
|---|---|---|---|
| is_new_user | Boolean | No | If true, app routes to Signup. If false, routes to Login. |
| auth_method_required | Enum | No | `PASSWORD`, `OTP`, `SOCIAL` |

---

#### API Name: User Signup
**Purpose**: Creates a new user account for a specific role and returns an authentication token.
**Used when**: New user completes the registration form.
**Endpoint**: `POST /api/v1/auth/signup`

**Request JSON**:
```json
{
  "name": "Arjun Sharma",
  "identifier": "9876543210",
  "role": "PARENT",
  "password": "hashed_password",
  "otp": "123456",
  "device_info": {
    "device_id": "XY123",
    "platform": "ANDROID"
  }
}
```

**Request Field Definitions**:
| Key | Type | Required | Description |
|---|---|---|---|
| name | String | Yes | User's full name |
| identifier | String | Yes | Registered email/mobile |
| role | Enum | Yes | Selected user role |
| password | String | No | Required if OTP is not used |
| otp | String | No | Required if verifying mobile |

**Success Response (201 Created)**:
```json
{
  "success": true,
  "message": "Account created successfully",
  "data": {
    "token": "jwt_access_token",
    "refresh_token": "jwt_refresh_token",
    "user_id": "usr_992",
    "role": "PARENT"
  }
}
```

---

#### API Name: User Login
**Purpose**: Authenticates an existing user and provides access tokens.
**Used when**: Existing user enters credentials.
**Endpoint**: `POST /api/v1/auth/login`

**Request JSON**:
```json
{
  "identifier": "9876543210",
  "role": "PARENT",
  "password": "hashed_password",
  "otp": "123456"
}
```

**Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "jwt_access_token",
    "user_id": "usr_992",
    "profile_completed": true
  }
}
```

**Response Field Definitions**:
| Key | Type | Nullable | Description |
|---|---|---|---|
| token | String | No | Bearer JWT for authenticated requests |
| profile_completed | Boolean | No | If false, routes to role-specific onboarding (School/Child) |

**Database Tables**: `users`, `user_roles`, `user_sessions`

**Caching Strategy**: Store `token` and `user_id` in encrypted local storage.

---

## Module: Role-Specific Experience (User Context)

### Screen: Admin Dashboard / Parent Dashboard (Post-Login Landing)
#### API Name: Get User Details & Onboarding State

### Purpose
Fetches the complete profile and state of the user after authentication. This API acts as the "Source of Truth" for the app to determine if the user needs to finish onboarding, which features to enable in the side drawer, and which themes are available.

### Used when:
- User logs in successfully
- App returns to foreground (re-validation)
- User switches roles (if applicable)

### Endpoint
`GET /api/v1/user/details`

### Authentication
**Required** (Bearer JWT)

### Headers
| Key | Type | Required | Description |
|---|---|---|---|
| Authorization | String | Yes | `Bearer <jwt_token>` |
| App-Version | String | Yes | To provide compatible onboarding steps |

### Success Response
#### Response Code
`200 OK`

#### Response JSON
```json
{
  "success": true,
  "data": {
    "personal_details": {
      "role": "ADMIN",
      "id": "usr_9921",
      "name": "Arjun Sharma",
      "profile_pic": "https://cdn.vidyaprayag.com/profiles/arjun.jpg",
      "email": "arjun@stxavier.edu",
      "mobile": "9876543210"
    },
    "onboarding_details": {
      "onboarding_status": "IN_PROGRESS",
      "total_steps": 3,
      "list_of_steps": [
        {
          "name": "Institutional Basics",
          "description": "Core school info and identity",
          "status": "COMPLETED",
          "icon": "school",
          "is_enabled": true,
          "is_required": true
        },
        {
          "name": "Branding & Visuals",
          "description": "Logo and portal themes",
          "status": "PENDING",
          "icon": "palette",
          "is_enabled": true,
          "is_required": true
        },
        {
          "name": "Academic Structure",
          "description": "Grade levels and curricula",
          "status": "LOCKED",
          "icon": "history_edu",
          "is_enabled": false,
          "is_required": true
        }
      ],
      "support_info": {
        "name": "VidyaPrayag Success Team",
        "description": "Available 9am - 6pm for setup help",
        "contact_number": "+91-9988776655",
        "contact_email": "support@vidyaprayag.com",
        "icon": "support_agent"
      },
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

### Response Field Definitions
| Key | Type | Nullable | Description |
|---|---|---|---|
| onboarding_status | Enum | No | `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED` |
| list_of_steps | Array[Obj]| No | Defines the multi-step onboarding journey UI |
| status (in step) | Enum | No | `COMPLETED`, `PENDING`, `LOCKED` |
| is_enabled (in feature)| Boolean | No | Toggles visibility of drawer/menu items |
| is_live (in feature) | Boolean | No | Shows 'Coming Soon' if False |
| app_themes | Array[Obj]| No | Dynamic theme selection based on account level |

---

### Database Tables Involved
| Table Name | Operation |
|---|---|
| users | Read |
| user_onboarding_state | Read |
| app_features_config | Read |
| support_directory | Read |

### Dependencies
| Dependency | Description |
|---|---|
| Onboarding Service | To calculate the current step sequence |
| Role Service | To filter appropriate `menu_features` |

### Caching Strategy
| Type | Details |
|---|---|
| Local Cache | Highly Recommended (Preferences) |
| Expiry | Data should be re-validated on every cold start |
| Purge Event | User profile update or Logout |

---

## Module: School Onboarding Flow (ADMIN)

### Screen: Step 1 & 2 (Basics & Branding)
#### API Name: Get Dynamic Onboarding Step Data

**Purpose**: Fetches UI metadata and drafted values for initial identity and look-and-feel steps.
**Endpoint**: `GET /api/v1/onboarding/step`
**Authentication**: Required

**Sample Request (Basics)**:
`GET /api/v1/onboarding/step?obStepType=BASIC`

**Sample Request (Branding)**:
`GET /api/v1/onboarding/step?obStepType=BRANDING`

**Success Response (200 OK)**:
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
      {
        "key": "school_name",
        "type": "SchoolName",
        "draft_exists": true,
        "draft_value": "St. Xavier Academy",
        "input_type": "line"
      }
    ]
  }
}
```

---

### Screen: Academic Setup (Step 3)
#### API Name: Get Academic Configuration Summary

**Purpose**: Fetches the high-level list of active classes for the school.
**Endpoint**: `GET /api/v1/onboarding/step`

**Sample Request**:
`GET /api/v1/onboarding/step?obStepType=ACADEMIC`

**Success Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "ob_step_type": "ACADEMIC",
    "current_step_count": 3,
    "total_step_count": 4,
    "list_of_active_classes": [
      { "id": "C10", "name": "Grade 10", "sections": ["A", "B"] }
    ]
  }
}
```

#### API Name: Get Class & Subject Details

**Purpose**: Fetches granular data for a specific class, including assigned teachers and subject codes.
**Endpoint**: `GET /api/v1/onboarding/academic/class-details`

**Sample Request**:
`GET /api/v1/onboarding/academic/class-details?classId=C10`

**Success Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "class_id": "C10",
    "class_name": "Grade 10",
    "total_subjects": 5,
    "list_of_subjects": [
      {
        "sub_name": "Modern Physics",
        "sub_code": "PHY-10",
        "teacher_assigned": "Dr. Robert Chen"
      },
      {
        "sub_name": "Calculus",
        "sub_code": "MAT-10",
        "teacher_assigned": "Dr. Sarah Henderson"
      }
    ]
  }
}
```

**Response Field Definitions**:
| Key | Type | Nullable | Description |
|---|---|---|---|
| class_id | String | No | Unique identifier for the cohort |
| sub_name | String | No | Human readable subject name |
| sub_code | String | No | Institutional curriculum code |
| teacher_assigned | String | Yes | Name of the primary instructor |

---

### Screen: Launch & Review (Step 4)
#### API Name: Get Onboarding Final Review Aggregation

**Purpose**: Final check of identity, docs, and modules.
**Endpoint**: `GET /api/v1/onboarding/step`

**Sample Request**:
`GET /api/v1/onboarding/step?obStepType=REVIEW`

**Success Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "ob_step_type": "REVIEW",
    "identity_details": { "institution_name": "St. Xavier Academy", "is_verified": false },
    "compliance_docs": [
      { "doc_id": "d_1", "doc_name": "Affiliation Cert", "is_verified": true }
    ],
    "list_of_selected_modules": [
      { "name": "Analytics", "isSelected": true }
    ]
  }
}
```

---

### API: Submit & Draft Handler
#### API Name: Post Onboarding Data

**Purpose**: Saves data for any given step.
**Endpoint**: `POST /api/v1/onboarding/submit`

**Sample Request (Basics Submission)**:
```json
{
  "ob_step_type": "BASIC",
  "is_final_submission": false,
  "data_payload": { "school_name": "St. Xavier", "email": "..." }
}
```

**Sample Request (Review/Final Submission)**:
```json
{
  "ob_step_type": "REVIEW",
  "is_final_submission": true,
  "data_payload": { "confirm_all": true }
}
```

### Success Response (200 OK)
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

### Response Field Definitions
| Key | Type | Nullable | Description |
|---|---|---|---|
| next_step | Enum | Yes | The next `obStepType` for navigation |
| redirect_to_home | Boolean| No | If true, user has finished all steps |

---

### Database Tables Involved
| Table Name | Operation |
|---|---|
| school_onboarding_drafts | Read / Upsert |
| school_academic_registry | Read / Insert |
| compliance_document_vault | Read / Insert |

### Dependencies
| Dependency | Description |
|---|---|
| Storage Service | Required for Doc/Logo uploads before submission |
| Verification Engine | To set `is_verified` flags in Review step |
