-- ============================================================================
-- VidyaPrayag — ALL-IN-ONE SCHEMA (CONSOLIDATED)
-- Generated: 2026-06-07
--
-- Run this single file in Supabase SQL Editor BEFORE running the seed.
-- It concatenates (in dependency order):
--   • docs/db/vidyasetu_schema.sql
--       Base operational schema (schools, app_users, children, students, fee_records, exam_results, ...)
--   • docs/db/migration_001_faculty_and_holiday_list.sql
--       Faculty + holiday_list tables
--   • docs/db/migration_002_segmentation_geo_assignments.sql
--       Announcement segmentation + teacher_subject_assignments + children.student_code
--   • docs/backend/sql/02_teacher_schema.sql
--       Teacher vertical (assessments, assessment_marks, syllabus_units, homework, homework_submissions, teacher_periods)
--   • scripts/schema-patch-2026-06-07.sql
--       Scholarships + scholarship_applications (closes RA-05)
--
-- Idempotent: every CREATE TABLE uses IF NOT EXISTS, every constraint add
-- is wrapped in DO $$ ... $$ guards. Safe to re-run.
-- ============================================================================


-- ============================================================================
-- BEGIN  docs/db/vidyasetu_schema.sql
-- Base operational schema (schools, app_users, children, students, fee_records, exam_results, ...)
-- ============================================================================
-- academic_calender:


create table if not exists public.academic_calendar (
  id uuid not null,
  school_id uuid not null,
  event_id text not null,
  date character varying(12) not null,
  day character varying(16) not null,
  event_title text not null,
  event_description text null,
  standard text null,
  is_holiday boolean not null default false,
  created_at timestamp without time zone not null,
  constraint academic_calendar_pkey primary key (id),
  constraint academic_calendar_event_id_unique unique (event_id)
) TABLESPACE pg_default;



-- admission_enquiries:



create table if not exists public.admission_enquiries (
  id uuid not null,
  school_id uuid not null,
  student_name text not null,
  parent_name text not null,
  parent_phone text null,
  parent_email text null,
  class_name text not null,
  date character varying(12) not null,
  status character varying(16) not null default 'new'::character varying,
  profile_pic text null,
  source character varying(32) null,
  notes text null,
  assigned_to uuid null,
  converted_at timestamp without time zone null,
  created_at timestamp without time zone not null,
  updated_at timestamp without time zone not null,
  constraint admission_enquiries_pkey primary key (id)
) TABLESPACE pg_default;




-- announcements:




create table if not exists public.announcements (
  id uuid not null,
  school_id uuid not null,
  event_id text not null,
  type character varying(16) not null,
  title text not null,
  sub_title text null,
  description text not null,
  event_image text null,
  date character varying(12) not null,
  synced_to_wa boolean not null default false,
  created_by uuid null,
  created_at timestamp without time zone not null,
  updated_at timestamp without time zone not null,
  constraint announcements_pkey primary key (id),
  constraint announcements_event_id_unique unique (event_id)
) TABLESPACE pg_default;





-- app_config:


create table if not exists public.app_config (
  key text not null,
  value text not null,
  updated_at timestamp without time zone not null,
  constraint app_config_pkey primary key (key)
) TABLESPACE pg_default;




-- app_users:

create table if not exists public.app_users (
  id uuid not null,
  linked_auth_user_id uuid null,
  school_id uuid null,
  role character varying(32) not null default 'parent'::character varying,
  full_name text not null,
  phone character varying(32) null,
  email character varying(255) null,
  password_hash text null,
  profile_pic_url text null,
  language_pref character varying(8) not null default 'hi'::character varying,
  is_phone_verified boolean not null default false,
  is_email_verified boolean not null default false,
  profile_completed boolean not null default false,
  is_active boolean not null default true,
  last_login_at timestamp without time zone null,
  created_at timestamp without time zone not null,
  updated_at timestamp without time zone not null,
  constraint app_users_pkey primary key (id),
  constraint app_users_email_unique unique (email),
  constraint app_users_phone_unique unique (phone)
) TABLESPACE pg_default;





-- attendance_records:


create table if not exists public.attendance_records (
  id uuid not null,
  school_id uuid not null,
  date character varying(12) not null,
  type character varying(16) not null,
  person_id text not null,
  grade text null,
  status character varying(16) not null,
  marked_by uuid null,
  created_at timestamp without time zone not null,
  constraint attendance_records_pkey primary key (id),
  constraint ux_att_records_unique unique (school_id, date, type, person_id)
) TABLESPACE pg_default;



-- auth_otps:


create table if not exists public.auth_otps (
  id uuid not null,
  identifier text not null,
  identifier_type character varying(8) not null,
  purpose character varying(24) not null default 'login'::character varying,
  code_hash text not null,
  code_salt text not null,
  sent_at timestamp without time zone not null,
  first_sent_at timestamp without time zone not null,
  expires_at timestamp without time zone not null,
  resend_count smallint not null default 0,
  attempt_count smallint not null default 0,
  max_attempts smallint not null default 5,
  max_resends smallint not null default 5,
  resend_window_secs integer not null default 3600,
  is_verified boolean not null default false,
  is_locked boolean not null default false,
  verified_at timestamp without time zone null,
  ip_address text null,
  user_agent text null,
  device_id text null,
  delivery_channel character varying(16) null,
  delivery_provider character varying(32) null,
  provider_message_id text null,
  created_at timestamp without time zone not null,
  updated_at timestamp without time zone not null,
  constraint auth_otps_pkey primary key (id),
  constraint ux_auth_otps_identifier_purpose unique (identifier, purpose)
) TABLESPACE pg_default;




-- children:


create table if not exists public.children (
  id uuid not null,
  parent_id uuid not null,
  school_id uuid null,
  child_name text not null,
  date_of_birth character varying(12) null,
  gender character varying(16) null,
  current_grade character varying(32) null,
  interests text not null default '[]'::text,
  profile_pic text null,
  overall_progress double precision not null default 0.0,
  current_level integer not null default 1,
  attendance_status character varying(16) not null default 'PRESENT'::character varying,
  is_active boolean not null default true,
  created_at timestamp without time zone not null,
  updated_at timestamp without time zone not null,
  constraint children_pkey primary key (id)
) TABLESPACE pg_default;




-- cms_landing_content:


create table if not exists public.cms_landing_content (
  key text not null,
  value text not null,
  updated_at timestamp without time zone not null,
  constraint cms_landing_content_pkey primary key (key)
) TABLESPACE pg_default;



-- exam_results:


create table if not exists public.exam_results (
  id uuid not null,
  school_id uuid not null,
  test text not null,
  class_name text not null,
  subject text not null,
  student_id text not null,
  student_name text not null,
  image_url text null,
  attendance character varying(8) not null default '0%'::character varying,
  score character varying(8) not null default ''::character varying,
  status character varying(16) not null default 'Pending'::character varying,
  trend character varying(8) not null default '0%'::character varying,
  created_at timestamp without time zone not null,
  updated_at timestamp without time zone not null,
  constraint exam_results_pkey primary key (id),
  constraint ux_exam_results_unique unique (school_id, test, class_name, subject, student_id)
) TABLESPACE pg_default;










-- fee_records:


create table if not exists public.fee_records (
  id uuid not null,
  parent_id uuid not null,
  child_id uuid null,
  school_id uuid null,
  title text not null,
  description text null,
  amount double precision not null default 0.0,
  currency character varying(8) not null default 'INR'::character varying,
  due_date character varying(12) null,
  status character varying(16) not null default 'DUE'::character varying,
  category character varying(32) not null default 'Tuition'::character varying,
  created_at timestamp without time zone not null,
  updated_at timestamp without time zone not null,
  constraint fee_records_pkey primary key (id)
) TABLESPACE pg_default;









-- leave_requests:

create table if not exists public.leave_requests (
  id uuid not null,
  school_id uuid not null,
  requester_id uuid null,
  requester_name text not null,
  requester_role character varying(16) not null default 'student'::character varying,
  date_from character varying(12) not null,
  date_to character varying(12) not null,
  reason text not null,
  image_url text null,
  status character varying(16) not null default 'Pending'::character varying,
  actioned_by uuid null,
  actioned_at timestamp without time zone null,
  created_at timestamp without time zone not null,
  updated_at timestamp without time zone not null,
  constraint leave_requests_pkey primary key (id)
) TABLESPACE pg_default;



-- message_threads:



create table if not exists public.message_threads (
  id uuid not null,
  school_id uuid not null,
  owner_user_id uuid not null,
  sender_name text not null,
  sender_role text not null,
  sender_image_url text null,
  icon_name text null,
  last_message text not null default ''::text,
  last_message_at timestamp without time zone not null,
  unread_count integer not null default 0,
  is_read boolean not null default true,
  created_at timestamp without time zone not null,
  updated_at timestamp without time zone not null,
  constraint message_threads_pkey primary key (id)
) TABLESPACE pg_default;





-- messages:


create table if not exists public.messages (
  id uuid not null,
  thread_id uuid not null,
  sender_id uuid null,
  body text not null,
  created_at timestamp without time zone not null,
  constraint messages_pkey primary key (id)
) TABLESPACE pg_default;



-- otp_delivery_attempts:


create table if not exists public.otp_delivery_attempts (
  id uuid not null,
  otp_id uuid null,
  identifier text not null,
  purpose character varying(24) not null,
  attempt_index integer not null,
  provider_name character varying(64) not null,
  channel character varying(16) not null,
  status character varying(16) not null,
  provider_message_id text null,
  http_status integer null,
  latency_ms integer not null default 0,
  reason text null,
  raw_response text null,
  created_at timestamp without time zone not null,
  constraint otp_delivery_attempts_pkey primary key (id)
) TABLESPACE pg_default;




-- ptm_class_progress:



create table if not exists public.ptm_class_progress (
  id uuid not null,
  ptm_event_id uuid not null,
  class_name text not null,
  teacher_name text not null,
  met_count integer not null default 0,
  total_count integer not null default 0,
  updated_at timestamp without time zone not null,
  constraint ptm_class_progress_pkey primary key (id),
  constraint ux_ptm_class_progress_unique unique (ptm_event_id, class_name)
) TABLESPACE pg_default;





-- ptm_events:


create table if not exists public.ptm_events (
  id uuid not null,
  school_id uuid not null,
  title text not null,
  date character varying(12) not null,
  slot text not null,
  expected_parents integer not null default 0,
  checked_in_parents integer not null default 0,
  invites_delivered integer not null default 0,
  read_receipts integer not null default 0,
  turnout integer not null default 0,
  total_met integer not null default 0,
  created_by uuid null,
  created_at timestamp without time zone not null,
  updated_at timestamp without time zone not null,
  constraint ptm_events_pkey primary key (id)
) TABLESPACE pg_default;



-- school_classes:


create table if not exists public.school_classes (
  id uuid not null,
  school_id uuid not null,
  code text not null,
  name text not null,
  sections text not null default '[]'::text,
  created_at timestamp without time zone not null,
  constraint school_classes_pkey primary key (id),
  constraint ux_classes_school_code unique (school_id, code)
) TABLESPACE pg_default;






-- school_media:



create table if not exists public.school_media (
  id uuid not null,
  school_id uuid not null,
  kind character varying(8) not null,
  url text not null,
  position integer not null default 0,
  size_bytes bigint not null default 0,
  uploaded_by uuid null,
  created_at timestamp without time zone not null,
  constraint school_media_pkey primary key (id)
) TABLESPACE pg_default;






-- school_onboarding_drafts:


create table if not exists public.school_onboarding_drafts (
  id uuid not null,
  user_id uuid not null,
  step_type character varying(16) not null,
  key text not null,
  value text not null,
  updated_at timestamp without time zone not null,
  constraint school_onboarding_drafts_pkey primary key (id),
  constraint ux_ob_drafts_user_step_key unique (user_id, step_type, key)
) TABLESPACE pg_default;





-- school_philosophy:


create table if not exists public.school_philosophy (
  school_id uuid not null,
  core_mission text null,
  learning_model text null,
  primary_language text null,
  public_profile boolean not null default true,
  updated_at timestamp without time zone not null,
  constraint school_philosophy_pkey primary key (school_id)
) TABLESPACE pg_default;




-- school_subjects:


create table if not exists public.school_subjects (
  id uuid not null,
  class_id uuid not null,
  sub_name text not null,
  sub_code text not null,
  teacher_assigned text null,
  created_at timestamp without time zone not null,
  constraint school_subjects_pkey primary key (id)
) TABLESPACE pg_default;





-- schools:

create table if not exists public.schools (
  id uuid not null,
  name text not null,
  slug text not null,
  board character varying(32) not null,
  medium character varying(32) not null,
  school_gender character varying(16) not null default 'co_ed'::character varying,
  contact_phone text null,
  contact_email text null,
  principal_name text null,
  principal_phone text null,
  principal_email text null,
  full_address text null,
  city text not null,
  district text not null,
  state text not null default 'Uttar Pradesh'::text,
  pincode text null,
  -- geo coordinates for parent /schools/discover Haversine sort (audit §1.3 LATLONG):
  -- kept in the BASE table so the discover query never throws 'column does not exist'
  -- even if migration_002 is skipped. migration_002's ADD COLUMN IF NOT EXISTS is then a no-op.
  latitude double precision null,
  longitude double precision null,
  logo_url text null,
  brand_color text not null default '#2563EB'::text,
  is_active boolean not null default true,
  onboarded_at timestamp without time zone null,
  created_at timestamp without time zone not null,
  updated_at timestamp without time zone not null,
  constraint schools_pkey primary key (id),
  constraint schools_slug_unique unique (slug)
) TABLESPACE pg_default;





-- storage_metrics:


create table if not exists public.storage_metrics (
  school_id uuid not null,
  total_storage text not null default '10 GB'::text,
  storage_used text not null default '0 B'::text,
  bytes_used bigint not null default 0,
  updated_at timestamp without time zone not null,
  constraint storage_metrics_pkey primary key (school_id)
) TABLESPACE pg_default;





-- students:


create table if not exists public.students (
  id uuid not null,
  school_id uuid not null,
  student_code text not null,
  full_name text not null,
  class_name text not null,
  section text not null default 'A'::text,
  roll_number text not null,
  profile_photo_url text null,
  is_active boolean not null default true,
  created_at timestamp without time zone not null,
  constraint students_pkey primary key (id),
  constraint students_student_code_unique unique (student_code)
) TABLESPACE pg_default;




-- user_sessions:


create table if not exists public.user_sessions (
  id uuid not null,
  user_id uuid not null,
  refresh_token_hash text not null,
  device_id text null,
  platform character varying(16) null,
  ip_address text null,
  user_agent text null,
  issued_at timestamp without time zone not null,
  expires_at timestamp without time zone not null,
  revoked_at timestamp without time zone null,
  last_used_at timestamp without time zone null,
  created_at timestamp without time zone not null,
  constraint user_sessions_pkey primary key (id),
  constraint user_sessions_refresh_token_hash_unique unique (refresh_token_hash)
) TABLESPACE pg_default;





-- users:

create table if not exists public.users (
  id uuid not null,
  name character varying(255) not null,
  contact character varying(255) not null,
  password character varying(255) not null,
  role character varying(50) not null,
  email character varying(255) null,
  phone character varying(50) null,
  password_hash character varying(255) null,
  is_phone_verified boolean not null default false,
  is_email_verified boolean not null default false,
  constraint users_pkey primary key (id),
  constraint users_contact_unique unique (contact)
) TABLESPACE pg_default;




-- whatsapp_logs:

create table if not exists public.whatsapp_logs (
  id uuid not null,
  school_id uuid not null,
  announcement_id text not null,
  job_id text not null,
  phone text not null,
  status character varying(16) not null default 'QUEUED'::character varying,
  provider_message_id text null,
  error_message text null,
  created_at timestamp without time zone not null,
  constraint whatsapp_logs_pkey primary key (id)
) TABLESPACE pg_default;

-- END    docs/db/vidyasetu_schema.sql


-- ============================================================================
-- BEGIN  docs/db/migration_001_faculty_and_holiday_list.sql
-- Faculty + holiday_list tables
-- ============================================================================
-- =============================================================================
-- Migration 001 — faculty + holiday_list
--
-- WHY THIS EXISTS
--   SCHOOL_SIDE_STATUS_REPORT §5.1 found that the Ktor backend's Exposed
--   mappings (server/.../db/Tables.kt) reference two tables that the uploaded
--   operational schema (docs/db/vidyasetu_schema.sql) does NOT create:
--       * faculty       -> FacultyTable        (teacher attendance + analytics)
--       * holiday_list  -> HolidayListTable     (/holidays + calendar summary)
--   Without these tables the following school features fail on production
--   Supabase with "relation does not exist" or always return empty:
--       * GET /api/v1/school/attendance/daily?type=faculty
--       * GET /api/v1/school/holidays
--       * GET /api/v1/school/calendar   (public/school holiday counts)
--       * Teacher performance analytics
--
-- HOW TO RUN
--   Supabase -> SQL Editor -> paste this whole file -> Run.
--   Safe to re-run: every statement is guarded with IF NOT EXISTS.
--
-- The column names/types below match server/.../db/Tables.kt EXACTLY so the
-- Exposed mappings line up with the real Postgres schema.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- faculty  ->  FacultyTable
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.faculty (
  id          uuid NOT NULL,
  school_id   uuid NOT NULL,
  external_id text NOT NULL,
  user_id     uuid NULL,
  name        text NOT NULL,
  profile_pic text NULL,
  department  text NULL,
  is_active   boolean NOT NULL DEFAULT true,
  created_at  timestamp without time zone NOT NULL,
  CONSTRAINT faculty_pkey PRIMARY KEY (id),
  CONSTRAINT faculty_external_id_unique UNIQUE (external_id)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS ix_faculty_school_id ON public.faculty (school_id);
CREATE INDEX IF NOT EXISTS ix_faculty_active    ON public.faculty (school_id, is_active);

-- ---------------------------------------------------------------------------
-- holiday_list  ->  HolidayListTable
--   type      = 'Public' | 'School'
--   frequency = 'weekly' | 'monthly' | 'yearly'
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.holiday_list (
  id         uuid NOT NULL,
  school_id  uuid NOT NULL,
  date       character varying(12) NOT NULL,
  title      text NOT NULL,
  type       character varying(16) NOT NULL,
  frequency  character varying(16) NOT NULL,
  created_at timestamp without time zone NOT NULL,
  CONSTRAINT holiday_list_pkey PRIMARY KEY (id)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS ix_holiday_list_school_id ON public.holiday_list (school_id);
CREATE INDEX IF NOT EXISTS ix_holiday_list_lookup    ON public.holiday_list (school_id, frequency);

-- =============================================================================
-- DONE. After running this, re-test:
--   GET /api/v1/school/holidays?filter_type=yearly
--   GET /api/v1/school/attendance/daily?type=faculty
--   GET /api/v1/school/calendar
-- All three should return data instead of an error/empty set.
-- =============================================================================

-- END    docs/db/migration_001_faculty_and_holiday_list.sql


-- ============================================================================
-- BEGIN  docs/db/migration_002_segmentation_geo_assignments.sql
-- Announcement segmentation + teacher_subject_assignments + children.student_code
-- ============================================================================
-- =============================================================================
-- Migration 002 — broadcast segmentation + geo discovery + teacher assignments
--                 + STUDENT-scope child<->student link + media-upload support
--
-- WHY THIS EXISTS
--   The backend gained new P1/P2 features (SCHOOL_SIDE_STATUS_REPORT §4.4, §5.5,
--   §5.6, §5.7) whose Exposed mappings in
--   server/.../db/Tables.kt now reference columns/tables that the live
--   Supabase schema (docs/db/vidyasetu_schema.sql) does NOT yet have. Without
--   this migration the backend will fail on Render (AUTO_CREATE_TABLES is off in
--   production) with errors like:
--       column "audience_type" of relation "announcements" does not exist
--       column "latitude" of relation "schools" does not exist
--       relation "teacher_subject_assignments" does not exist
--
-- HOW TO RUN
--   Supabase -> SQL Editor -> paste this whole file -> Run.
--   Safe to re-run: every statement is guarded (IF NOT EXISTS / ADD COLUMN IF
--   NOT EXISTS). It only ADDS columns/tables; it never drops or rewrites data.
--
-- RUN ORDER (full provisioning):
--   1. vidyasetu_schema.sql
--   2. migration_001_faculty_and_holiday_list.sql
--   3. migration_002_segmentation_geo_assignments.sql   <-- this file
--
-- Column names/types match server/.../db/Tables.kt EXACTLY.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) announcements — broadcast audience segmentation  (report §5.6)
--    audience_type  : ALL_SCHOOL | CLASS | SECTION | SUBJECT | STUDENT | CUSTOM
--    audience_filter: JSON scope, e.g. {"class_name":"Grade 5","section":"A"}
--    author_role    : owner role; teacher broadcasts are scoped to their classes
-- ---------------------------------------------------------------------------
ALTER TABLE public.announcements
  ADD COLUMN IF NOT EXISTS audience_type   character varying(16) NOT NULL DEFAULT 'ALL_SCHOOL';
ALTER TABLE public.announcements
  ADD COLUMN IF NOT EXISTS audience_filter text NULL;
ALTER TABLE public.announcements
  ADD COLUMN IF NOT EXISTS author_role     character varying(16) NOT NULL DEFAULT 'school_admin';

-- ---------------------------------------------------------------------------
-- 2) schools — geo coordinates for parent "near me" discovery (report §4.4/§5.7)
-- ---------------------------------------------------------------------------
ALTER TABLE public.schools
  ADD COLUMN IF NOT EXISTS latitude  double precision NULL;
ALTER TABLE public.schools
  ADD COLUMN IF NOT EXISTS longitude double precision NULL;

-- Optional helper index for bounding-box pre-filtering before Haversine sort.
CREATE INDEX IF NOT EXISTS ix_schools_geo ON public.schools (latitude, longitude);

-- ---------------------------------------------------------------------------
-- 3) teacher_subject_assignments — structured teacher<->class<->subject (§5.5)
--    Replaces free-text school_subjects.teacher_assigned.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.teacher_subject_assignments (
  id           uuid NOT NULL,
  school_id    uuid NOT NULL,
  class_id     uuid NULL,
  class_name   text NOT NULL,
  section      character varying(8) NOT NULL DEFAULT 'A',
  subject_id   uuid NULL,
  subject      text NOT NULL,
  teacher_id   uuid NULL,
  teacher_name text NULL,
  is_active    boolean NOT NULL DEFAULT true,
  created_at   timestamp without time zone NOT NULL,
  updated_at   timestamp without time zone NOT NULL,
  CONSTRAINT teacher_subject_assignments_pkey PRIMARY KEY (id),
  CONSTRAINT ux_tsa_unique UNIQUE (school_id, class_name, section, subject, teacher_name)
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS ix_tsa_school    ON public.teacher_subject_assignments (school_id, is_active);
CREATE INDEX IF NOT EXISTS ix_tsa_teacher   ON public.teacher_subject_assignments (school_id, teacher_id);
CREATE INDEX IF NOT EXISTS ix_tsa_class     ON public.teacher_subject_assignments (school_id, class_name);

-- ---------------------------------------------------------------------------
-- 4) children — link a parent's child to the school's canonical student record
--    so STUDENT-scoped broadcasts can target exact students (report §5.6 note).
--    student_code references students.student_code (text, unique).
-- ---------------------------------------------------------------------------
ALTER TABLE public.children
  ADD COLUMN IF NOT EXISTS student_code text NULL;

CREATE INDEX IF NOT EXISTS ix_children_student_code ON public.children (student_code);

-- =============================================================================
-- DONE. After running this, the following should work without errors:
--   POST /api/v1/school/announcements           (with audience_type/filter)
--   POST /api/v1/school/announcements/sync-whatsapp   (segmented recipients)
--   GET/POST/DELETE /api/v1/school/teacher-assignments
--   GET /api/v1/parent/schools/discover?lat=&lng=
-- =============================================================================

-- END    docs/db/migration_002_segmentation_geo_assignments.sql


-- =============================================================================
-- BEGIN  migration_003_leave_workflow_and_two_party_messaging.sql
-- =============================================================================
-- WHY THIS EXISTS
--   AUTO_CREATE_TABLES is OFF in production (Postgres/Supabase). The KMP server
--   (DatabaseFactory.kt) only runs SchemaUtils.createMissingTablesAndColumns on
--   the local SQLite path; against Postgres it runs NO migration. So every new
--   column added to server/.../db/Tables.kt must be mirrored here as a reviewed
--   ALTER TABLE, then executed by a human in the Supabase SQL Editor. Without
--   this, the backend fails in production with errors like:
--       column "class_id" of relation "leave_requests" does not exist
--       column "conversation_id" of relation "message_threads" does not exist
--
-- HOW TO RUN
--   Supabase -> SQL Editor -> paste -> Run. Safe to re-run: every statement is
--   guarded with ADD COLUMN IF NOT EXISTS. It only ADDS nullable columns; it
--   never drops, rewrites, or backfills data.
--
-- Column names/types match server/.../db/Tables.kt EXACTLY.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) leave_requests — class/teacher/child/parent routing context  (RA-44)
--    A parent files leave for a child; the class teacher decides; admin can
--    override. These columns carry the routing context so the request lands in
--    the right teacher's queue and the right parent gets notified. All nullable
--    because legacy student-filed rows pre-date this workflow.
--      class_id    : FK-ish ref to school_classes.id (nullable; name is canonical)
--      class_name  : denormalized class label the teacher owns
--      section     : denormalized section label
--      teacher_id  : the class teacher who should action the request
--      child_id    : the children.id the leave is for (parent-filed path)
--      parent_id   : the app_users.id of the filing parent (notify target)
-- ---------------------------------------------------------------------------
ALTER TABLE public.leave_requests
  ADD COLUMN IF NOT EXISTS class_id   uuid NULL;
ALTER TABLE public.leave_requests
  ADD COLUMN IF NOT EXISTS class_name character varying(64) NULL;
ALTER TABLE public.leave_requests
  ADD COLUMN IF NOT EXISTS section    character varying(16) NULL;
ALTER TABLE public.leave_requests
  ADD COLUMN IF NOT EXISTS teacher_id uuid NULL;
ALTER TABLE public.leave_requests
  ADD COLUMN IF NOT EXISTS child_id   uuid NULL;
ALTER TABLE public.leave_requests
  ADD COLUMN IF NOT EXISTS parent_id  uuid NULL;

-- Helper index: a teacher pulling their pending leave queue filters by
-- (school_id, teacher_id, status). Keep it lean and guarded.
CREATE INDEX IF NOT EXISTS ix_leave_requests_teacher
  ON public.leave_requests (school_id, teacher_id, status);

-- ---------------------------------------------------------------------------
-- 2) message_threads / messages — two-party conversation engine  (RA-51)
--    The original model was single-owner (one thread row per owner). Real
--    parent<->teacher conversations need BOTH participants to see the same
--    logical conversation, so we introduce a shared conversation_id plus a
--    peer_user_id on each owner's thread row. MessagingCore.kt creates one
--    thread row per participant, both carrying the same conversation_id.
--      message_threads.conversation_id : shared id across both participants'
--                                        rows (NULL legacy rows fall back to id)
--      message_threads.peer_user_id    : the other participant's app_users.id
--      messages.conversation_id        : stamp each message with its conversation
--    All nullable so legacy single-owner rows keep working (conversationKey()
--    falls back to the thread's own id when conversation_id IS NULL).
-- ---------------------------------------------------------------------------
ALTER TABLE public.message_threads
  ADD COLUMN IF NOT EXISTS conversation_id uuid NULL;
ALTER TABLE public.message_threads
  ADD COLUMN IF NOT EXISTS peer_user_id    uuid NULL;
ALTER TABLE public.messages
  ADD COLUMN IF NOT EXISTS conversation_id uuid NULL;

-- Helper indexes: thread lookup by conversation (both participants' rows) and
-- message fetch by conversation. Guarded + lean.
CREATE INDEX IF NOT EXISTS ix_message_threads_conversation
  ON public.message_threads (school_id, conversation_id);
CREATE INDEX IF NOT EXISTS ix_message_threads_owner_peer
  ON public.message_threads (owner_user_id, peer_user_id);
CREATE INDEX IF NOT EXISTS ix_messages_conversation
  ON public.messages (conversation_id, created_at);

-- =============================================================================
-- DONE. After running this, the following should work without errors:
--   POST /api/v1/parent/leave            (parent files leave for a child)
--   GET/POST /api/v1/teacher/leave-requests   (teacher decides)
--   GET/POST /api/v1/teacher/messages/*  (two-party + class broadcast)
--   GET/POST /api/v1/parent/messages/*   (two-party reply path)
--   GET/POST /api/v1/school/messages/*   (admin two-party path)
-- =============================================================================

-- END    migration_003_leave_workflow_and_two_party_messaging.sql


-- ============================================================================
-- BEGIN  docs/backend/sql/02_teacher_schema.sql
-- Teacher vertical (assessments, assessment_marks, syllabus_units, homework, homework_submissions, teacher_periods)
-- ============================================================================
-- =============================================================================
-- VidyaPrayag — TEACHER VERTICAL SCHEMA (additive, idempotent)
-- Version: 1.0  |  Engine: PostgreSQL (Supabase)
--
-- WHAT THIS FILE DOES
-- -------------------
-- Adds the tables the TEACHER portal needs (master rebuild doc Step 7 / §9 /
-- gap G1) so the `api/v1/teacher/*` routes have somewhere to read/write. These
-- are consumed by the KMP client's `shared/feature/teacher` data layer.
--
--   1.  assessments            — a teacher-authored test/exam for one
--                                class+section+subject, with a max-marks
--                                denominator (the numeric, normalized
--                                counterpart to the string-scored
--                                `exam_results` used by the school-admin
--                                Results screen).
--   2.  assessment_marks       — per-student score for an assessment.
--   3.  syllabus_units         — chapter/topic coverage per class+subject.
--   4.  homework               — assignments authored by a teacher.
--   5.  homework_submissions   — per-student submission rows (for the
--                                submitted/total ratio on homework cards).
--   6.  teacher_periods        — optional weekly timetable (teacher Home
--                                "today's periods"). Empty-safe: no rows = no
--                                periods rendered (never fabricated).
--
-- Attendance is NOT re-modelled here — the teacher portal writes student
-- attendance into the existing `attendance_records` table (type='student',
-- marked_by = teacher's app_users.id).
--
-- ALL `CREATE TABLE` statements use `IF NOT EXISTS` so this file is safe to
-- re-run. Run it in Supabase Dashboard → SQL Editor → New Query → paste → Run.
--
-- ⚠️  PREREQUISITES (RA-63): this fragment is already bundled — the earlier
--     sections of THIS all-in-one file create `schools`, `app_users`,
--     `students`, and `teacher_subject_assignments`, so just run this file
--     top-to-bottom. Do NOT use the legacy root "VIDYASETU v2.1" schema
--     (archived to docs/_archive/...ABANDONED.sql).
--
-- SCOPING MODEL
-- -------------
-- Every teacher endpoint resolves the caller's school from app_users.school_id
-- and constrains class+subject access to the caller's rows in
-- `teacher_subject_assignments` (matched on teacher_id = app_users.id, with a
-- teacher_name fallback for legacy free-text rows). The denormalised
-- class_name / section / subject columns below mirror that table so reads are
-- single-table and fast.
-- =============================================================================


-- =============================================================================
-- SECTION 0 — EXTENSIONS (already enabled by base schema, kept for safety)
-- =============================================================================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";


-- =============================================================================
-- SECTION 1 — assessments  (teacher test/exam definitions)
-- =============================================================================
CREATE TABLE IF NOT EXISTS assessments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    teacher_id  UUID REFERENCES app_users(id) ON DELETE SET NULL,
    class_name  TEXT NOT NULL,
    section     VARCHAR(8) NOT NULL DEFAULT 'A',
    subject     TEXT NOT NULL,
    name        TEXT NOT NULL,
    max_marks   INTEGER NOT NULL DEFAULT 100,
    exam_date   VARCHAR(12),                       -- YYYY-MM-DD
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_assessments_school_class_subject
    ON assessments (school_id, class_name, section, subject);


-- =============================================================================
-- SECTION 2 — assessment_marks  (per-student scores)
--
-- marks is NULL until a score is entered. The route clamps it to
-- [0, assessments.max_marks]. One (assessment, student) pair is unique so a
-- re-submit updates in place rather than duplicating.
-- =============================================================================
CREATE TABLE IF NOT EXISTS assessment_marks (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id UUID NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    student_id    TEXT NOT NULL,                   -- students.student_code
    student_name  TEXT NOT NULL,
    marks         DOUBLE PRECISION,                -- NULL = not entered yet
    entered_by    UUID REFERENCES app_users(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_assessment_marks_unique UNIQUE (assessment_id, student_id)
);


-- =============================================================================
-- SECTION 3 — syllabus_units  (chapter/topic coverage)
-- =============================================================================
CREATE TABLE IF NOT EXISTS syllabus_units (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    class_name  TEXT NOT NULL,
    section     VARCHAR(8) NOT NULL DEFAULT 'A',
    subject     TEXT NOT NULL,
    title       TEXT NOT NULL,
    position    INTEGER NOT NULL DEFAULT 0,
    is_covered  BOOLEAN NOT NULL DEFAULT FALSE,
    covered_on  VARCHAR(12),                       -- YYYY-MM-DD
    covered_by  UUID REFERENCES app_users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_syllabus_units_scope
    ON syllabus_units (school_id, class_name, section, subject, position);


-- =============================================================================
-- SECTION 4 — homework  (teacher assignments)
-- =============================================================================
CREATE TABLE IF NOT EXISTS homework (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    teacher_id  UUID REFERENCES app_users(id) ON DELETE SET NULL,
    class_name  TEXT NOT NULL,
    section     VARCHAR(8) NOT NULL DEFAULT 'A',
    subject     TEXT NOT NULL,
    title       TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    due_date    VARCHAR(12) NOT NULL,              -- YYYY-MM-DD
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_homework_school_class_subject
    ON homework (school_id, class_name, section, subject);


-- =============================================================================
-- SECTION 5 — homework_submissions  (per-student submission rows)
-- =============================================================================
CREATE TABLE IF NOT EXISTS homework_submissions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    homework_id  UUID NOT NULL REFERENCES homework(id) ON DELETE CASCADE,
    student_id   TEXT NOT NULL,                    -- students.student_code
    status       VARCHAR(16) NOT NULL DEFAULT 'submitted',  -- submitted | graded | late
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_homework_submissions_unique UNIQUE (homework_id, student_id)
);


-- =============================================================================
-- SECTION 6 — teacher_periods  (optional weekly timetable)
--
-- weekday is 1..7 (Mon..Sun) to match java.time.DayOfWeek.value. Empty-safe:
-- with no rows the teacher Home renders an honest empty "today's periods".
-- =============================================================================
CREATE TABLE IF NOT EXISTS teacher_periods (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    teacher_id  UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    weekday     INTEGER NOT NULL,                  -- 1=Mon … 7=Sun
    start_time  VARCHAR(8) NOT NULL,               -- "HH:mm"
    end_time    VARCHAR(8) NOT NULL,               -- "HH:mm"
    class_name  TEXT NOT NULL,
    section     VARCHAR(8) NOT NULL DEFAULT 'A',
    subject     TEXT NOT NULL,
    room        TEXT NOT NULL DEFAULT '',
    position    INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_teacher_periods_teacher_weekday
    ON teacher_periods (teacher_id, weekday, position);


-- =============================================================================
-- SECTION 99 — BACK-FILL: tables added to the Ktor backend after
-- `01_supplementary_schema.sql` was last edited (the SQL doc had drifted
-- behind db/Tables.kt). These are created by SchemaUtils on SQLite dev and by
-- AUTO_CREATE_TABLES=true on first Postgres boot, but are spelled out here so a
-- locked-down production DB (no auto-create) can be migrated by hand. All
-- IF NOT EXISTS, so harmless if they already exist.
--   • teacher_subject_assignments   (structured teacher⇄class⇄subject graph)
--   • children, fee_records         (parent ecosystem)
--   • leave_requests, ptm_events, ptm_class_progress,
--     message_threads, messages, exam_results   (school ecosystem)
-- The authoritative column list is db/Tables.kt; run AUTO_CREATE_TABLES=true
-- once on a staging DB and diff if you need the exact generated DDL.
-- =============================================================================
-- (Intentionally left as a documented pointer rather than duplicated DDL, to
--  avoid two diverging sources of truth. db/Tables.kt + SchemaUtils is the
--  generator; this file owns only the teacher-vertical tables above.)


-- =============================================================================
-- DONE — teacher-vertical schema v1.0 loaded.
--
-- CHANGELOG
--   v1.0  (2026-06-06) — assessments, assessment_marks, syllabus_units,
--                        homework, homework_submissions, teacher_periods.
-- =============================================================================

-- END    docs/backend/sql/02_teacher_schema.sql


-- ============================================================================
-- BEGIN  scripts/schema-patch-2026-06-07.sql
-- Scholarships + scholarship_applications (closes RA-05)
-- ============================================================================
-- =============================================================================
-- schema-patch-2026-06-07.sql
-- VidyaPrayag — Schema Gap Patch (PostgreSQL / Supabase)
-- -----------------------------------------------------------------------------
-- WHEN TO RUN
--   Apply this file BEFORE seed-2026-06-07.sql on any database provisioned via
--   the canonical recipe (docs/db/PROVISION.sql). The recipe today creates
--   36 of the 38 tables registered in server/.../db/Tables.kt; this patch
--   adds the two missing ones so the seed has somewhere to insert and so the
--   server's validateSchema() boot gate passes in strict (AUTO_CREATE_TABLES=
--   false) mode.
--
-- IDEMPOTENT
--   Every statement uses IF NOT EXISTS / IF EXISTS or DO blocks. Running this
--   file twice is a no-op. No data is destroyed.
--
-- WHAT'S IN HERE
--   PATCH-001  CREATE TABLE scholarships                    (RE-AUDIT RA-05)
--   PATCH-002  CREATE TABLE scholarship_applications        (RE-AUDIT RA-05)
--   PATCH-003  OPTIONAL fee_records.status CHECK enum       (RE-AUDIT RA-11)
--              -- shipped commented-out; uncomment only after confirming all
--              -- existing rows already use the canonical values.
--   VERIFY     Final SELECTs confirming both tables exist and are empty.
--
-- COLUMN SHAPES
--   Mirror server/.../db/Tables.kt:754-781 exactly. Column types match the
--   base schema's "timestamp without time zone" convention (Exposed
--   timestamp() default). Defaults match the Kotlin .default(...) values.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- PATCH-001 — scholarships table (re-audit RA-05)
--
-- Source of truth: server/src/main/kotlin/com.littlebridge.enrollplus/db/Tables.kt
--                  object ScholarshipsTable : UUIDTable("scholarships", "id") { … }
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scholarships (
    id            uuid                        PRIMARY KEY DEFAULT gen_random_uuid(),
    title         text                        NOT NULL,
    description   text                        NOT NULL,
    amount        text                        NOT NULL,
    time_left     text                        NOT NULL DEFAULT '',
    category      varchar(48)                 NOT NULL DEFAULT 'Merit Based',
    is_critical   boolean                     NOT NULL DEFAULT false,
    position      integer                     NOT NULL DEFAULT 0,
    is_active     boolean                     NOT NULL DEFAULT true,
    created_at    timestamp without time zone NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at    timestamp without time zone NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

COMMENT ON TABLE  scholarships             IS 'Catalogue of scholarships shown on the parent Scholarships screen. Global (not school-scoped). Added by schema-patch-2026-06-07 to close RE-AUDIT RA-05.';
COMMENT ON COLUMN scholarships.amount      IS 'Display string, e.g. "Rs. 45,000". Not numeric — surfaces formatted to UI as-is.';
COMMENT ON COLUMN scholarships.time_left   IS 'Display string, e.g. "3d : 12h". Computed/refreshed elsewhere.';

CREATE INDEX IF NOT EXISTS ix_scholarships_active_position
    ON scholarships (is_active, position);

-- -----------------------------------------------------------------------------
-- PATCH-002 — scholarship_applications table (re-audit RA-05)
--
-- Source of truth: server/src/main/kotlin/com.littlebridge.enrollplus/db/Tables.kt
--                  object ScholarshipApplicationsTable : UUIDTable(...) { … }
--
-- parent_id references app_users(id). The Exposed model does NOT declare an
-- FK (RE-AUDIT RA-18), but at the SQL layer we add ON DELETE CASCADE so that
-- when an app_users row is deleted the orphan applications do not strand.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scholarship_applications (
    id           uuid                        PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id    uuid                        NOT NULL,
    institution  text                        NOT NULL,
    program      text                        NOT NULL,
    status       varchar(24)                 NOT NULL DEFAULT 'Received',
    icon_name    varchar(32)                 NOT NULL DEFAULT 'school',
    position     integer                     NOT NULL DEFAULT 0,
    created_at   timestamp without time zone NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at   timestamp without time zone NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

-- Add the FK in a DO block so re-runs do not fail when it already exists.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_scholarship_applications_parent'
    ) THEN
        ALTER TABLE scholarship_applications
            ADD CONSTRAINT fk_scholarship_applications_parent
            FOREIGN KEY (parent_id) REFERENCES app_users(id) ON DELETE CASCADE;
    END IF;
END$$;

COMMENT ON TABLE  scholarship_applications             IS 'Parent-scoped applications shown next to the catalogue. parent_id is app_users.id. Added by schema-patch-2026-06-07 to close RE-AUDIT RA-05.';
COMMENT ON COLUMN scholarship_applications.status      IS 'Received | Under Review | Shortlisted (free-text; no CHECK).';
COMMENT ON COLUMN scholarship_applications.icon_name   IS 'UI glyph key; e.g. school | trophy | book.';

CREATE INDEX IF NOT EXISTS ix_scholarship_applications_parent_position
    ON scholarship_applications (parent_id, position);

-- -----------------------------------------------------------------------------
-- PATCH-003 (OPTIONAL, COMMENTED-OUT) — fee_records.status CHECK enum
--
-- RE-AUDIT RA-11: fee_records.status is a free-form varchar today, but every
-- consumer (ParentFeesRouting.kt) branches on a finite set: DUE | PAID |
-- OVERDUE. Uncomment to enforce the constraint AT DDL — but verify first that
-- no legacy row violates it, otherwise the ALTER will fail.
--
-- Pre-flight:
--   SELECT DISTINCT status FROM fee_records ORDER BY 1;
--   -- expected output: just DUE, PAID, OVERDUE (case-sensitive)
-- -----------------------------------------------------------------------------
-- DO $$
-- BEGIN
--     IF NOT EXISTS (
--         SELECT 1 FROM pg_constraint
--         WHERE conname = 'ck_fee_records_status_enum'
--     ) THEN
--         ALTER TABLE fee_records
--             ADD CONSTRAINT ck_fee_records_status_enum
--             CHECK (status IN ('DUE','PAID','OVERDUE'));
--     END IF;
-- END$$;

COMMIT;

-- =============================================================================
-- VERIFICATION — run after COMMIT. Both tables must report 'exists'; the FK
-- must report 'present'; the optional CHECK status is informational.
-- =============================================================================
SELECT 'scholarships'             AS object,
       CASE WHEN to_regclass('public.scholarships')             IS NOT NULL THEN 'exists' ELSE 'MISSING' END AS state
UNION ALL
SELECT 'scholarship_applications',
       CASE WHEN to_regclass('public.scholarship_applications') IS NOT NULL THEN 'exists' ELSE 'MISSING' END
UNION ALL
SELECT 'fk_scholarship_applications_parent',
       CASE WHEN EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_scholarship_applications_parent')
            THEN 'present' ELSE 'MISSING' END
UNION ALL
SELECT 'ck_fee_records_status_enum (optional)',
       CASE WHEN EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_fee_records_status_enum')
            THEN 'present' ELSE 'not-enabled' END;

-- =============================================================================
-- END schema-patch-2026-06-07.sql
-- =============================================================================

-- END    scripts/schema-patch-2026-06-07.sql

-- ============================================================================
-- POST-RUN VERIFICATION — should show all the seed-needed tables exist
-- ============================================================================
SELECT 'tables_present' AS check_name, COUNT(*) AS n FROM information_schema.tables
WHERE table_schema = 'public' AND table_name IN (
  'academic_calendar','announcements','app_users','assessment_marks','assessments',
  'attendance_records','children','exam_results','faculty','fee_records',
  'holiday_list','homework','homework_submissions','scholarship_applications',
  'scholarships','school_classes','school_philosophy','school_subjects','schools',
  'storage_metrics','students','syllabus_units','teacher_subject_assignments'
);
-- expected: n = 23

-- migration_003 column-presence check — all 9 added columns must report 'present'
SELECT 'leave_requests.class_id'        AS object, CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='leave_requests'  AND column_name='class_id')        THEN 'present' ELSE 'MISSING' END AS state
UNION ALL SELECT 'leave_requests.class_name',  CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='leave_requests'  AND column_name='class_name')  THEN 'present' ELSE 'MISSING' END
UNION ALL SELECT 'leave_requests.section',     CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='leave_requests'  AND column_name='section')     THEN 'present' ELSE 'MISSING' END
UNION ALL SELECT 'leave_requests.teacher_id',  CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='leave_requests'  AND column_name='teacher_id')  THEN 'present' ELSE 'MISSING' END
UNION ALL SELECT 'leave_requests.child_id',    CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='leave_requests'  AND column_name='child_id')    THEN 'present' ELSE 'MISSING' END
UNION ALL SELECT 'leave_requests.parent_id',   CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='leave_requests'  AND column_name='parent_id')   THEN 'present' ELSE 'MISSING' END
UNION ALL SELECT 'message_threads.conversation_id', CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='message_threads' AND column_name='conversation_id') THEN 'present' ELSE 'MISSING' END
UNION ALL SELECT 'message_threads.peer_user_id',    CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='message_threads' AND column_name='peer_user_id')    THEN 'present' ELSE 'MISSING' END
UNION ALL SELECT 'messages.conversation_id',        CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='messages'        AND column_name='conversation_id') THEN 'present' ELSE 'MISSING' END;

