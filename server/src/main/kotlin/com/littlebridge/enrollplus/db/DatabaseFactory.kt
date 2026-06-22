/*
 * File: DatabaseFactory.kt
 * Module: db
 *
 * Connects the Ktor backend to the configured database:
 *   - PRODUCTION / STAGING : Supabase Postgres (DATABASE_URL set)
 *   - LOCAL DEV (default)  : SQLite file `data.db` in CWD
 *
 * IMPORTANT: against Postgres we DO NOT run any schema migration from code.
 * The source of truth (RA-63) is the canonical all-in-one, run manually in the
 * Supabase SQL Editor in this exact order — see docs/db/PROVISION.sql and
 * scripts/README-RUN-ORDER.md:
 *     1.  scripts/schema-all-in-one-2026-06-07.sql      (every table; built from
 *         docs/db/vidyasetu_schema.sql + migration_001/002/003 + patches)
 *     2.  scripts/seed-2026-06-07.sql                   (test data)
 *
 * DO NOT use the legacy root "VIDYASETU v2.1" schema — it has been archived to
 * docs/_archive/supabase_schema_VIDYASETU_v2.1_ABANDONED.sql and does NOT match
 * Tables.kt. Nothing in this codebase reads it.
 *
 * Why?  Letting an ORM mutate production schema silently is a recipe for
 * downtime.  All schema changes go through a reviewed SQL migration PR
 * and are executed by a human in the Supabase dashboard.
 *
 * Against SQLite (no DATABASE_URL), we *do* call
 * SchemaUtils.createMissingTablesAndColumns(...) so the server boots on
 * a fresh clone with zero setup.
 *
 * ENVIRONMENT VARIABLES READ
 *   DATABASE_URL       : full JDBC or postgres:// URL
 *   DATABASE_USER      : Postgres user (optional if encoded in URL)
 *   DATABASE_PASSWORD  : Postgres password (optional if encoded in URL)
 *   DB_POOL_SIZE       : HikariCP pool size (default 5)
 *   APP_SEED_CMS       : "true" to seed/upsert landing+app_config rows
 *                        (default "true" — these are CMS strings, safe to seed)
 */
package com.littlebridge.enrollplus.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.Properties

object DatabaseFactory {

    /**
     * Config resolution order for a given key (first non-blank wins):
     *   1. .env file (via dotenv) — the documented production path
     *   2. OS environment variable (Render / Docker / shell export)
     *   3. local.properties at the repo root — DX convenience so devs who keep
     *      DATABASE_URL/USER/PASSWORD in local.properties (the same file Android
     *      Studio uses) don't silently fall back to SQLite. local.properties is
     *      git-ignored, so it's a safe place for laptop secrets.
     *
     * Without (3), a developer who put DB creds only in local.properties would
     * see isPostgres=false and have all their writes land in a local SQLite
     * data.db instead of Supabase — exactly the "nothing shows up in the DB"
     * symptom this resolver prevents.
     */
    private val localProps: Properties by lazy {
        val props = Properties()
        // Search the working dir and a couple of parents — `./gradlew :server:run`
        // runs with CWD = repo root, but be forgiving about where it's launched.
        val candidates = listOf(
            File("local.properties"),
            File("../local.properties"),
            File(System.getProperty("user.dir"), "local.properties")
        )
        candidates.firstOrNull { it.isFile }?.let { f ->
            runCatching { f.inputStream().use(props::load) }
                .onSuccess { println("DB_INIT: Loaded fallback config from ${f.absolutePath}") }
                .onFailure { System.err.println("DB_INIT: Could not read ${f.absolutePath}: ${it.message}") }
        }
        props
    }

    private fun resolve(dotenv: io.github.cdimascio.dotenv.Dotenv, key: String): String? =
        (dotenv[key] ?: System.getenv(key) ?: localProps.getProperty(key))
            ?.let(::sanitizeConfigValue)
            ?.takeIf { it.isNotBlank() }

    /**
     * Java .properties / some .env editors keep the surrounding quotes as part of
     * the value (e.g. DATABASE_URL="jdbc:postgresql://..."). If we feed that raw
     * value with quotes into the JDBC URL builder, it no longer starts with
     * "jdbc:" / "postgresql://", falls into the else-branch, and we end up with a
     * doubled, broken URL like:
     *     jdbc:postgresql://"jdbc:postgresql://host:5432/db?..."
     * Strip leading/trailing whitespace and a single pair of matching quotes so
     * the value is always clean regardless of how the dev wrote it.
     */
    private fun sanitizeConfigValue(raw: String): String {
        var v = raw.trim()
        if (v.length >= 2 &&
            ((v.startsWith("\"") && v.endsWith("\"")) ||
                (v.startsWith("'") && v.endsWith("'")))
        ) {
            v = v.substring(1, v.length - 1).trim()
        }
        return v
    }

    /** All tables the backend reads/writes. Order matters for SQLite FKs. */
    private val allTables = arrayOf(
        AppUsersTable,
        AuthOtpsTable,
        OtpDeliveryAttemptsTable,
        UserSessionsTable,
        LandingContentTable,
        AppConfigTable,
        SchoolsTable,
        OnboardingDraftsTable,
        SchoolClassesTable,
        SchoolSubjectsTable,
        TeacherSubjectAssignmentsTable,
        AnnouncementsTable,
        WhatsappLogsTable,
        AdmissionEnquiriesTable,
        SchoolPhilosophyTable,
        SchoolMediaTable,
        StorageMetricsTable,
        AcademicCalendarTable,
        HolidayListTable,
        FacultyTable,
        AttendanceRecordsTable,
        StudentsTable,
        ChildrenTable,
        FeeRecordsTable,
        // School ecosystem (school_api_spec.artifact.md)
        LeaveRequestsTable,
        PtmEventsTable,
        PtmClassProgressTable,
        MessageThreadsTable,
        MessagesTable,
        ExamResultsTable,
        // Teacher vertical (master doc Step 7 / gap G1)
        AssessmentsTable,
        AssessmentMarksTable,
        SyllabusUnitsTable,
        HomeworkTable,
        HomeworkSubmissionsTable,
        TeacherPeriodsTable,
        // Parent scholarships (audit §4.2/§5.2 — DB-backed, replaces hardcoded list)
        ScholarshipsTable,
        ScholarshipApplicationsTable,
        // Notification spine + push registry + link approval (audit part-2 RA-41/42/46/48/50)
        NotificationsTable,
        DeviceTokensTable,
        // OTPSender SMS-gateway integration (feature/setup_notification):
        // device registry + SMS request queue. FK-free (device_id is a soft
        // reference) so declaration order vs other tables does not matter.
        OtpGatewayDevicesTable,
        SmsRequestsTable,
        ParentChildLinksTable,
        // Non-teaching staff vertical (RA-S17 — Admin People sub-tabs)
        NonTeachingStaffTable,
        // Parents Portal — Profile tab "Missions & Achievements" (optional, CMS-fallback safe)
        ParentAchievementsTable,
        // Academic Calendar platform (VP-CAL — centralized planning & scheduling)
        CalendarEventsTable,
        AcademicYearsTable
    )

    /** True when DATABASE_URL is set → we're talking to Postgres / Supabase. */
    var isPostgres: Boolean = false
        private set

    fun init() {
        val dotenv = dotenv {
            ignoreIfMalformed = true
            ignoreIfMissing = true
        }

        val databaseUrl = resolve(dotenv, "DATABASE_URL")

        val dataSource = if (databaseUrl != null) {
            isPostgres = true
            createPostgresDataSource(
                databaseUrl,
                user = resolve(dotenv, "DATABASE_USER"),
                password = resolve(dotenv, "DATABASE_PASSWORD"),
                poolSize = resolve(dotenv, "DB_POOL_SIZE")?.toIntOrNull() ?: 5
            )
        } else {
            System.err.println(
                "DB_INIT: No DATABASE_URL found in .env, environment, or local.properties — " +
                    "falling back to LOCAL SQLite (data.db). Writes will NOT reach Supabase! " +
                    "Set DATABASE_URL (+ DATABASE_USER / DATABASE_PASSWORD) to use Postgres."
            )
            createSqliteDataSource()
        }

        Database.connect(dataSource)

        val autoCreateRaw = resolve(dotenv, "AUTO_CREATE_TABLES")
        val autoCreate = autoCreateRaw.equals("true", ignoreCase = true)

        println("DB_INIT: isPostgres=$isPostgres, AUTO_CREATE_TABLES='$autoCreateRaw' -> $autoCreate")

        // Try to create tables if in SQLite OR if explicitly requested in Postgres
        if (!isPostgres || autoCreate) {
            println("DB_INIT: Running SchemaUtils.createMissingTablesAndColumns for ${allTables.size} tables...")
            try {
                transaction {
                    SchemaUtils.createMissingTablesAndColumns(*allTables)
                }
                println("DB_INIT: Schema check/creation completed.")
            } catch (e: Exception) {
                System.err.println("DB_INIT_ERROR: Schema creation failed!")
                e.printStackTrace()
                // If this fails, we probably can't proceed with seeding either
            }
        } else {
            println("DB_INIT: Skipping auto-creation (AUTO_CREATE_TABLES is not 'true').")
        }

        // Boot-time schema completeness validation (audit finding A). In
        // Postgres without auto-create, a missing table means a guessed/
        // incomplete provisioning recipe was used and dependent routes would
        // 500 at runtime. We surface that loudly at boot instead.
        validateSchema(autoCreate)

        // CMS seed (landing + app_config). Always idempotent — only inserts
        // missing keys; never overwrites operator-edited values.
        val seedCms = (resolve(dotenv, "APP_SEED_CMS") ?: "true")
            .equals("true", ignoreCase = true)
        
        if (seedCms) {
            println("DB_INIT: Running CMS seed...")
            try {
                // We wrap the seed in a check to see if the table exists first to avoid crash loops
                CmsSeed.ensureLandingAndConfig()
                println("DB_INIT: CMS seed completed successfully.")
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("relation", ignoreCase = true) && msg.contains("does not exist", ignoreCase = true)) {
                    System.err.println("DB_INIT_WARNING: CMS Seeding skipped because tables are missing.")
                    System.err.println("DB_INIT_TIP: Set AUTO_CREATE_TABLES=true on Render to create tables automatically.")
                } else {
                    System.err.println("DB_INIT_ERROR: CMS Seeding failed with unexpected error!")
                    e.printStackTrace()
                    throw e
                }
            }
        }

        // Operational demo seed (audit finding B): one working credential per
        // profile type + minimal operational data, so a fresh deploy is
        // immediately loginable instead of empty/unlogin-able. Idempotent.
        val seedDemo = (resolve(dotenv, "APP_SEED_DEMO") ?: "true")
            .equals("true", ignoreCase = true)

        if (seedDemo) {
            println("DB_INIT: Running operational demo seed...")
            try {
                DemoSeed.ensureDemoData()
                println("DB_INIT: Demo seed completed successfully.")
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("relation", ignoreCase = true) && msg.contains("does not exist", ignoreCase = true)) {
                    System.err.println("DB_INIT_WARNING: Demo seeding skipped because tables are missing.")
                    System.err.println("DB_INIT_TIP: Set AUTO_CREATE_TABLES=true on Render to create tables automatically.")
                } else {
                    System.err.println("DB_INIT_ERROR: Demo seeding failed with unexpected error!")
                    e.printStackTrace()
                    // Non-fatal: CMS + schema are already in place; don't crash-loop.
                }
            }
        }
    }

    /**
     * Audit finding A: verify every one of the 36 registered tables exists.
     * In Postgres without auto-create, any missing table means an incomplete
     * provisioning recipe was used (see docs/db/PROVISION.sql for the only
     * complete one) and dependent routes would 500 at runtime — so we refuse
     * to boot. In SQLite/dev or when AUTO_CREATE_TABLES handled creation, we
     * only warn.
     */
    private fun validateSchema(autoCreate: Boolean) {
        try {
            val existing = transaction {
                SchemaUtils.listTables().map { it.substringAfterLast('.').lowercase().trim('"') }.toSet()
            }
            val missing = allTables
                .map { it.tableName.substringAfterLast('.').lowercase().trim('"') }
                .filter { it !in existing }

            if (missing.isEmpty()) {
                println("DB_INIT: Schema validation OK — all ${allTables.size} tables present.")
                return
            }

            val msg = "DB_INIT: Schema validation FOUND ${missing.size} MISSING table(s): ${missing.sorted()}"
            if (isPostgres && !autoCreate) {
                System.err.println(msg)
                System.err.println("DB_INIT_TIP: Provision with docs/db/PROVISION.sql (the only complete recipe) " +
                    "or set AUTO_CREATE_TABLES=true.")
                throw IllegalStateException(
                    "Refusing to boot: Postgres schema is incomplete (missing ${missing.size} tables). " +
                    "See docs/db/PROVISION.sql."
                )
            } else {
                System.err.println("$msg (non-fatal: SQLite/dev or auto-create enabled).")
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            System.err.println("DB_INIT_WARNING: Schema validation could not run: ${e.message}")
        }
    }

    private fun createPostgresDataSource(
        databaseUrl: String,
        user: String?,
        password: String?,
        poolSize: Int
    ): HikariDataSource {
        // Defensively strip any surrounding quotes/whitespace that may have slipped
        // through (e.g. a value read straight from a .properties file). Without this
        // a quoted value would not match the prefixes below and we'd double-prefix it
        // into jdbc:postgresql://"jdbc:postgresql://..." (the classic broken URL).
        val cleanUrl = sanitizeConfigValue(databaseUrl)

        // Accept both forms:
        //   postgresql://USER:PASS@HOST:5432/DB?sslmode=require
        //   jdbc:postgresql://HOST:5432/DB?sslmode=require
        //   postgres://USER:PASS@HOST:5432/DB
        val jdbcUrl = when {
            cleanUrl.startsWith("jdbc:") -> cleanUrl
            cleanUrl.startsWith("postgres://") ->
                "jdbc:" + cleanUrl.replaceFirst("postgres://", "postgresql://")
            cleanUrl.startsWith("postgresql://") -> "jdbc:$cleanUrl"
            else -> "jdbc:postgresql://$cleanUrl"
        }

        // Auto-append SSL mode and PgBouncer threshold if missing
        val finalJdbcUrl = buildString {
            append(jdbcUrl)
            val separator = if (jdbcUrl.contains("?")) "&" else "?"
            
            if (!jdbcUrl.contains("sslmode=") && isPostgres) {
                append(separator).append("sslmode=require")
            }
            
            if (!contains("prepareThreshold=")) {
                append(if (contains("?")) "&" else "?").append("prepareThreshold=0")
            }
            
            if (!contains("currentSchema=")) {
                append(if (contains("?")) "&" else "?").append("currentSchema=public")
            }
        }

        println("DB_INIT: Connecting to $finalJdbcUrl")

        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            this.jdbcUrl = finalJdbcUrl
            if (!user.isNullOrBlank()) this.username = user
            if (!password.isNullOrBlank()) this.password = password
            maximumPoolSize = poolSize
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            // Sensible defaults for Supabase (pooled, IPv4 PgBouncer port 6543).
            addDataSourceProperty("ApplicationName", "vidyaprayag-ktor")
            addDataSourceProperty("reWriteBatchedInserts", "true")
            connectionTimeout = 30_000
            validationTimeout = 5_000
            maxLifetime = 30 * 60 * 1000L
            validate()
        }
        return HikariDataSource(config)
    }

    private fun createSqliteDataSource(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:data.db"
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_SERIALIZABLE"
            validate()
        }
        return HikariDataSource(config)
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
