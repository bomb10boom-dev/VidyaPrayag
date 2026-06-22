/*
 * File: ResultsRouting.kt
 * Module: feature.school
 *
 * Endpoints (all JWT):
 *   GET  /api/v1/school/results?test=...&class=...&subject=...
 *   POST /api/v1/school/results
 *
 * Spec ref: school_api_spec.artifact.md §Module: Results
 *
 * GET aggregates the filtered slice of `exam_results` (school + test +
 * class + subject) into a `summary` block:
 *   class_average      → mean of `score` values, where each score is converted
 *                        to a 0-100 number via scoreToNumeric() so both numeric
 *                        ("98", "92%") AND grade-style ("A+", "B") scores count.
 *   average_trend      → REAL signal: this test's class average minus the
 *                        previous test's class average for the same class +
 *                        subject (the test whose rows were created most recently
 *                        before the current one). Rendered as a signed delta
 *                        e.g. "+2.4%" / "-1.0%" / "+0.0%" when no prior test.
 *   exceeding_count    → count where status == "Exceeding"
 *   meeting_count      → count where status == "Meeting"
 *   below_count        → count where status == "Below"
 *
 * Authorization: both endpoints require a school role + school via
 * call.requireSchoolContext(); every query is scoped to the resolved school_id.
 *
 * `available_tests` / `available_classes` / `available_subjects` come from
 * CMS key `school_results_filters` so ops control curriculum metadata
 * without backend redeploys.
 *
 * POST does a portable upsert (Exposed has no cross-DB UPSERT yet) — we
 * select on the unique tuple `(school, test, class, subject, student_id)`
 * and either UPDATE the existing row or INSERT a new one.  The endpoint
 * is fully idempotent: publishing the same test twice updates rather
 * than duplicates.
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.AppConfigTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.ExamResultsTable
import com.littlebridge.enrollplus.db.StudentsTable
import com.littlebridge.enrollplus.feature.notifications.Notify
import com.littlebridge.enrollplus.feature.notifications.NotifyRecipients
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

// ---------------- DTOs ----------------

@Serializable
data class ResultsFiltersDto(
    @SerialName("selected_test") val selectedTest: String,
    @SerialName("selected_class") val selectedClass: String,
    @SerialName("selected_subject") val selectedSubject: String,
    @SerialName("available_tests") val availableTests: List<String>,
    @SerialName("available_classes") val availableClasses: List<String>,
    @SerialName("available_subjects") val availableSubjects: List<String>
)

@Serializable
data class ResultsSummaryDto(
    @SerialName("class_average") val classAverage: String,
    @SerialName("average_trend") val averageTrend: String,
    @SerialName("exceeding_count") val exceedingCount: Int,
    @SerialName("meeting_count") val meetingCount: Int,
    @SerialName("below_count") val belowCount: Int
)

@Serializable
data class ResultStudentDto(
    val id: String,
    val name: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val attendance: String,
    val score: String,
    val status: String,
    val trend: String
)

@Serializable
data class ResultsResponse(
    val filters: ResultsFiltersDto,
    val summary: ResultsSummaryDto,
    val students: List<ResultStudentDto>
)

@Serializable
data class PublishResultsRow(
    @SerialName("student_id") val studentId: String,
    @SerialName("student_name") val studentName: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val attendance: String? = null,
    val score: String,
    val status: String? = null,
    val trend: String? = null
)

@Serializable
data class PublishResultsDto(
    val test: String,
    @SerialName("class") val className: String,
    val subject: String,
    val results: List<PublishResultsRow>
)

@Serializable
data class PublishResultsResponse(val upserted: Int)

// ---------------- helpers ----------------

private val LENIENT_JSON = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

private fun cmsArrayStrings(rootKey: String, leafKey: String): List<String> {
    val raw = AppConfigTable.selectAll()
        .where { AppConfigTable.key eq rootKey }
        .singleOrNull()
        ?.get(AppConfigTable.value)
        ?: return emptyList()
    return runCatching {
        val root = LENIENT_JSON.parseToJsonElement(raw)
        (root as? kotlinx.serialization.json.JsonObject)?.get(leafKey)?.let {
            (it as? JsonArray)?.mapNotNull { el -> el.jsonPrimitive.content }
        } ?: emptyList()
    }.getOrElse { emptyList() }
}

/**
 * Convert a score string to a 0-100 number for averaging.
 *  - "98", "92%", "88.5" → their numeric value
 *  - "A+"/"A"/"A-"/"B+".../"F" → conventional grade-point percentages
 *  - "Pending"/blank/unknown → null (excluded from the average)
 */
private fun scoreToNumeric(score: String): Double? {
    val s = score.trim()
    if (s.isEmpty()) return null
    s.trimEnd('%').toDoubleOrNull()?.let { return it }
    return when (s.uppercase()) {
        "A+" -> 98.0
        "A"  -> 93.0
        "A-" -> 90.0
        "B+" -> 87.0
        "B"  -> 83.0
        "B-" -> 80.0
        "C+" -> 77.0
        "C"  -> 73.0
        "C-" -> 70.0
        "D+" -> 67.0
        "D"  -> 63.0
        "D-" -> 60.0
        "F"  -> 50.0
        else -> null
    }
}

/** Mean of the numeric-convertible scores in a row set, or null if none. */
private fun averageOf(scores: List<String>): Double? {
    val nums = scores.mapNotNull { scoreToNumeric(it) }
    return if (nums.isEmpty()) null else nums.average()
}

/**
 * Compute the signed average-trend string for the current test by comparing its
 * class average against the PREVIOUS test (same school/class/subject, a
 * different test name) whose rows were created most recently before the current
 * test's earliest row.
 *
 * MUST be called inside an active dbQuery {} transaction.
 *
 * Returns:
 *   "+2.4%" / "-1.0%"  when a comparable prior test exists
 *   "+0.0%"            when there's no prior test or no comparable averages
 */
private fun computePreviousTestTrend(
    schoolId: UUID,
    currentTest: String,
    className: String,
    subject: String,
    currentRows: List<org.jetbrains.exposed.sql.ResultRow>,
    currentAvg: Double?
): String {
    if (currentAvg == null || currentRows.isEmpty()) return "+0.0%"

    // Earliest creation timestamp among the current test's rows.
    val currentAnchor = currentRows.minOf { it[ExamResultsTable.createdAt] }

    // All rows for the same class+subject but a DIFFERENT test, created before
    // the current test, grouped by test name. We pick the test whose latest row
    // is closest (but prior) to the current anchor.
    val priorRows = ExamResultsTable.selectAll()
        .where {
            (ExamResultsTable.schoolId eq schoolId) and
                (ExamResultsTable.className eq className) and
                (ExamResultsTable.subject eq subject) and
                (ExamResultsTable.test neq currentTest)
        }
        .toList()
        .filter { it[ExamResultsTable.createdAt].isBefore(currentAnchor) }

    if (priorRows.isEmpty()) return "+0.0%"

    val previousTest = priorRows
        .groupBy { it[ExamResultsTable.test] }
        .maxByOrNull { (_, rows) -> rows.maxOf { it[ExamResultsTable.createdAt] } }
        ?: return "+0.0%"

    val previousAvg = averageOf(previousTest.value.map { it[ExamResultsTable.score] })
        ?: return "+0.0%"

    val delta = currentAvg - previousAvg
    val sign = if (delta >= 0) "+" else "-"
    return "$sign%.1f%%".format(kotlin.math.abs(delta))
}

fun Route.resultsRouting() {
    authenticate("jwt") {
        route("/api/v1/school/results") {

            // -------- GET --------
            get {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId

                val test = call.request.queryParameters["test"].orEmpty()
                val classFilter = call.request.queryParameters["class"].orEmpty()
                val subject = call.request.queryParameters["subject"].orEmpty()

                val payload = dbQuery {
                    val availableTests = cmsArrayStrings("school_results_filters", "available_tests")
                    val availableClasses = cmsArrayStrings("school_results_filters", "available_classes")
                    val availableSubjects = cmsArrayStrings("school_results_filters", "available_subjects")

                    val resolvedTest    = if (test.isBlank())    availableTests.firstOrNull().orEmpty()    else test
                    val resolvedClass   = if (classFilter.isBlank()) availableClasses.firstOrNull().orEmpty() else classFilter
                    val resolvedSubject = if (subject.isBlank()) availableSubjects.firstOrNull().orEmpty() else subject

                    val rows = ExamResultsTable.selectAll()
                        .where {
                            (ExamResultsTable.schoolId eq schoolId) and
                                (ExamResultsTable.test eq resolvedTest) and
                                (ExamResultsTable.className eq resolvedClass) and
                                (ExamResultsTable.subject eq resolvedSubject)
                        }
                        .orderBy(ExamResultsTable.studentName, SortOrder.ASC)
                        .toList()

                    val students = rows.map { r ->
                        ResultStudentDto(
                            id = r[ExamResultsTable.studentId],
                            name = r[ExamResultsTable.studentName],
                            imageUrl = r[ExamResultsTable.imageUrl],
                            attendance = r[ExamResultsTable.attendance],
                            score = r[ExamResultsTable.score],
                            status = r[ExamResultsTable.status],
                            trend = r[ExamResultsTable.trend]
                        )
                    }

                    val currentAvg = averageOf(rows.map { it[ExamResultsTable.score] })
                    val classAverage = if (currentAvg == null) "0.0" else "%.1f".format(currentAvg)

                    // Real trend: find the previous test (same school/class/subject,
                    // a DIFFERENT test name) whose rows were created most recently
                    // BEFORE the current test's earliest row, and diff the averages.
                    val averageTrend = computePreviousTestTrend(
                        schoolId = schoolId,
                        currentTest = resolvedTest,
                        className = resolvedClass,
                        subject = resolvedSubject,
                        currentRows = rows,
                        currentAvg = currentAvg
                    )

                    val exceeding = rows.count { it[ExamResultsTable.status].equals("Exceeding", true) }
                    val meeting   = rows.count { it[ExamResultsTable.status].equals("Meeting",   true) }
                    val below     = rows.count { it[ExamResultsTable.status].equals("Below",     true) }

                    ResultsResponse(
                        filters = ResultsFiltersDto(
                            selectedTest = resolvedTest,
                            selectedClass = resolvedClass,
                            selectedSubject = resolvedSubject,
                            availableTests = availableTests,
                            availableClasses = availableClasses,
                            availableSubjects = availableSubjects
                        ),
                        summary = ResultsSummaryDto(
                            classAverage = classAverage,
                            averageTrend = averageTrend,
                            exceedingCount = exceeding,
                            meetingCount = meeting,
                            belowCount = below
                        ),
                        students = students
                    )
                }
                call.ok(payload, message = "Results fetched successfully")
            }

            // -------- PUBLISH (bulk upsert) --------
            post {
                val ctx = call.requireSchoolContext() ?: return@post
                val schoolId = ctx.schoolId
                val req = call.receive<PublishResultsDto>()
                if (req.test.isBlank() || req.className.isBlank() || req.subject.isBlank()) {
                    call.fail("test, class, subject are required"); return@post
                }
                if (req.results.isEmpty()) {
                    call.fail("results must not be empty"); return@post
                }

                val now = Instant.now()
                val upserted = dbQuery {
                    var count = 0
                    req.results.forEach { row ->
                        // Default student_name lookup against `students` table if not provided.
                        val resolvedName = row.studentName ?: StudentsTable.selectAll()
                            .where { StudentsTable.studentCode eq row.studentId }
                            .singleOrNull()
                            ?.get(StudentsTable.fullName)
                            ?: row.studentId
                        val resolvedAttendance = row.attendance ?: "0%"
                        val resolvedStatus = row.status ?: "Pending"
                        val resolvedTrend = row.trend ?: "0%"
                        val resolvedImage = row.imageUrl

                        val existing = ExamResultsTable.selectAll().where {
                            (ExamResultsTable.schoolId eq schoolId) and
                                (ExamResultsTable.test eq req.test) and
                                (ExamResultsTable.className eq req.className) and
                                (ExamResultsTable.subject eq req.subject) and
                                (ExamResultsTable.studentId eq row.studentId)
                        }.singleOrNull()

                        if (existing == null) {
                            ExamResultsTable.insert {
                                it[id] = UUID.randomUUID()
                                it[ExamResultsTable.schoolId] = schoolId
                                it[test] = req.test
                                it[className] = req.className
                                it[subject] = req.subject
                                it[studentId] = row.studentId
                                it[studentName] = resolvedName
                                it[imageUrl] = resolvedImage
                                it[attendance] = resolvedAttendance
                                it[score] = row.score
                                it[status] = resolvedStatus
                                it[trend] = resolvedTrend
                                it[createdAt] = now
                                it[updatedAt] = now
                            }
                        } else {
                            ExamResultsTable.update({ ExamResultsTable.id eq existing[ExamResultsTable.id].value }) {
                                it[studentName] = resolvedName
                                if (resolvedImage != null) it[imageUrl] = resolvedImage
                                it[attendance] = resolvedAttendance
                                it[score] = row.score
                                it[status] = resolvedStatus
                                it[trend] = resolvedTrend
                                it[updatedAt] = now
                            }
                        }
                        count++
                    }
                    count
                }

                // RA-S09: notify the affected class parents that results were published — parity
                // with the teacher marks-submit path (TeacherRoutingTasks.kt). Scoped to this
                // school for multi-tenant isolation; best-effort, never fails the publish.
                runCatching {
                    val parents = NotifyRecipients.parentsOfClass(schoolId, req.className)
                    if (parents.isNotEmpty()) {
                        Notify.toUsers(
                            userIds = parents,
                            category = "marks",
                            title = "Results published",
                            body = "Marks for \"${req.test}\" (${req.subject}) have been published.",
                            schoolId = schoolId,
                            actorId = ctx.userId,
                            deepLink = "parent/academics/marks",
                            refType = "assessment",
                            refId = req.test,
                        )
                    }
                }

                call.created(PublishResultsResponse(upserted), message = "Results published")
            }
        }
    }
}
