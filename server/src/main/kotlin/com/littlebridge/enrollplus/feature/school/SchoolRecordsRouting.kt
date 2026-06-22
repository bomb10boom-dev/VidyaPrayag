/*
 * File: SchoolRecordsRouting.kt
 * Module: feature.school
 *
 * RA-52: admin Records rollups. ROOT: the Records tab showed Coverage live but
 * Attendance / Marks / Fee as `VComingSoon` placeholders even though
 * `attendance_records`, `assessments`/`assessment_marks` and `fee_records`
 * already held real data — the admin had no school-wide read of any of them.
 *
 * Endpoints (JWT + school-scoped via requireSchoolContext; school_id from JWT):
 *   GET /api/v1/school/attendance/summary   today + class-wise present/absent rollup
 *   GET /api/v1/school/marks/summary        per-assessment averages (school)
 *   GET /api/v1/school/fees/ledger          paid/due/overdue totals + recent rows
 *
 * Every aggregate is constrained to ctx.schoolId, so an admin only ever sees
 * their OWN school's records (IDOR-safe). These are pure reads of existing
 * tables — no schema change.
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.AssessmentMarksTable
import com.littlebridge.enrollplus.db.AssessmentsTable
import com.littlebridge.enrollplus.db.AttendanceRecordsTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.FeeRecordsTable
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

// ───────────────────────────── DTOs ─────────────────────────────

@Serializable
data class AttendanceClassRow(
    val grade: String,
    val present: Int,
    val absent: Int,
    val late: Int,
    val total: Int,
    @SerialName("rate") val rate: Int
)

@Serializable
data class AttendanceSummaryDto(
    @SerialName("latest_date") val latestDate: String? = null,
    @SerialName("present") val present: Int,
    @SerialName("absent") val absent: Int,
    @SerialName("late") val late: Int,
    @SerialName("total") val total: Int,
    @SerialName("rate") val rate: Int,
    @SerialName("by_class") val byClass: List<AttendanceClassRow>
)

@Serializable
data class MarksAssessmentRow(
    val subject: String,
    @SerialName("assessment") val assessmentName: String,
    @SerialName("class_name") val className: String,
    @SerialName("average") val average: Double,
    @SerialName("max_marks") val maxMarks: Int,
    @SerialName("graded_count") val gradedCount: Int,
    @SerialName("exam_date") val examDate: String? = null,
    @SerialName("is_published") val isPublished: Boolean
)

@Serializable
data class MarksSummaryDto(
    @SerialName("assessment_count") val assessmentCount: Int,
    @SerialName("overall_average_pct") val overallAveragePct: Int,
    val assessments: List<MarksAssessmentRow>
)

@Serializable
data class FeeRow(
    val title: String,
    val amount: Double,
    val currency: String,
    val status: String,
    @SerialName("due_date") val dueDate: String? = null,
    val category: String
)

@Serializable
data class FeeLedgerDto(
    @SerialName("paid_total") val paidTotal: Double,
    @SerialName("due_total") val dueTotal: Double,
    @SerialName("overdue_total") val overdueTotal: Double,
    @SerialName("paid_count") val paidCount: Int,
    @SerialName("due_count") val dueCount: Int,
    @SerialName("overdue_count") val overdueCount: Int,
    val currency: String,
    val recent: List<FeeRow>
)

// ─────────────────────────── routing ────────────────────────────

fun Route.schoolRecordsRouting() {
    authenticate("jwt") {

        // ── attendance rollup ─────────────────────────────────────────────────
        get("/api/v1/school/attendance/summary") {
            val ctx = call.requireSchoolContext() ?: return@get
            val dto = dbQuery {
                // The most recent date with student attendance in this school.
                val latest = AttendanceRecordsTable.selectAll()
                    .where { (AttendanceRecordsTable.schoolId eq ctx.schoolId) and (AttendanceRecordsTable.type eq "student") }
                    .orderBy(AttendanceRecordsTable.date, SortOrder.DESC)
                    .firstOrNull()?.get(AttendanceRecordsTable.date)

                if (latest == null) {
                    AttendanceSummaryDto(null, 0, 0, 0, 0, 0, emptyList())
                } else {
                    val rows = AttendanceRecordsTable.selectAll().where {
                        (AttendanceRecordsTable.schoolId eq ctx.schoolId) and
                            (AttendanceRecordsTable.type eq "student") and
                            (AttendanceRecordsTable.date eq latest)
                    }.map {
                        Triple(
                            it[AttendanceRecordsTable.grade] ?: "—",
                            it[AttendanceRecordsTable.status].lowercase(),
                            1
                        )
                    }
                    val present = rows.count { it.second == "present" }
                    val absent = rows.count { it.second == "absent" }
                    val late = rows.count { it.second == "late" }
                    val total = rows.size
                    val rate = if (total > 0) ((present + late) * 100) / total else 0

                    val byClass = rows.groupBy { it.first }.map { (grade, group) ->
                        val p = group.count { it.second == "present" }
                        val a = group.count { it.second == "absent" }
                        val l = group.count { it.second == "late" }
                        val t = group.size
                        AttendanceClassRow(grade, p, a, l, t, if (t > 0) ((p + l) * 100) / t else 0)
                    }.sortedBy { it.grade }

                    AttendanceSummaryDto(latest, present, absent, late, total, rate, byClass)
                }
            }
            call.ok(dto, message = "Attendance summary")
        }

        // ── marks rollup ────────────────────────────────────────────────────────
        get("/api/v1/school/marks/summary") {
            val ctx = call.requireSchoolContext() ?: return@get
            val dto = dbQuery {
                val assessments = AssessmentsTable.selectAll()
                    .where { (AssessmentsTable.schoolId eq ctx.schoolId) and (AssessmentsTable.isActive eq true) }
                    .orderBy(AssessmentsTable.examDate, SortOrder.DESC)
                    .toList()

                val rows = assessments.map { a ->
                    val aId = a[AssessmentsTable.id].value
                    val max = a[AssessmentsTable.maxMarks]
                    val marks = AssessmentMarksTable.selectAll()
                        .where { AssessmentMarksTable.assessmentId eq aId }
                        .mapNotNull { it[AssessmentMarksTable.marks] }
                    val avg = if (marks.isNotEmpty()) marks.sum() / marks.size else 0.0
                    MarksAssessmentRow(
                        subject = a[AssessmentsTable.subject],
                        assessmentName = a[AssessmentsTable.name],
                        className = a[AssessmentsTable.className],
                        average = (avg * 100).toLong() / 100.0,   // 2-dp
                        maxMarks = max,
                        gradedCount = marks.size,
                        examDate = a[AssessmentsTable.examDate],
                        isPublished = a[AssessmentsTable.isPublished]
                    )
                }
                // Overall average as a percentage across graded assessments.
                val pctEach = rows.filter { it.gradedCount > 0 && it.maxMarks > 0 }
                    .map { (it.average / it.maxMarks) * 100.0 }
                val overall = if (pctEach.isNotEmpty()) (pctEach.sum() / pctEach.size).toInt() else 0
                MarksSummaryDto(assessmentCount = rows.size, overallAveragePct = overall, assessments = rows)
            }
            call.ok(dto, message = "Marks summary")
        }

        // ── fee ledger ────────────────────────────────────────────────────────────
        get("/api/v1/school/fees/ledger") {
            val ctx = call.requireSchoolContext() ?: return@get
            val dto = dbQuery {
                val all = FeeRecordsTable.selectAll()
                    .where { FeeRecordsTable.schoolId eq ctx.schoolId }
                    .orderBy(FeeRecordsTable.createdAt, SortOrder.DESC)
                    .map {
                        FeeRow(
                            title = it[FeeRecordsTable.title],
                            amount = it[FeeRecordsTable.amount],
                            currency = it[FeeRecordsTable.currency],
                            status = it[FeeRecordsTable.status],
                            dueDate = it[FeeRecordsTable.dueDate],
                            category = it[FeeRecordsTable.category]
                        )
                    }
                fun sumOf(status: String) = all.filter { it.status.equals(status, ignoreCase = true) }.sumOf { it.amount }
                fun countOf(status: String) = all.count { it.status.equals(status, ignoreCase = true) }
                val currency = all.firstOrNull()?.currency ?: "INR"
                FeeLedgerDto(
                    paidTotal = sumOf("PAID"),
                    dueTotal = sumOf("DUE"),
                    overdueTotal = sumOf("OVERDUE"),
                    paidCount = countOf("PAID"),
                    dueCount = countOf("DUE"),
                    overdueCount = countOf("OVERDUE"),
                    currency = currency,
                    recent = all.take(20)
                )
            }
            call.ok(dto, message = "Fee ledger")
        }
    }
}
