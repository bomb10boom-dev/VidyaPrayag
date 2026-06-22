/*
 * File: AdmissionRouting.kt
 * Module: feature.admissions
 *
 * Endpoints:
 *   GET   /api/v1/admissions/enquiries/summary       (JWT — admin/staff)
 *   GET   /api/v1/admissions/enquiries               (JWT — paginated)
 *   POST  /api/v1/admissions/enquiries               (JWT — create)
 *   PATCH /api/v1/admissions/enquiries/{id}/status   (JWT — update status)
 *
 * Spec ref: vidya_prayag_api_spec2.artifact.md §Admission Enquiries Dashboard
 *
 * Efficiency metric:  converted / (converted + follow_ups)  → "85%"
 * Pagination:         page=1, limit=20 defaults.
 */
package com.littlebridge.enrollplus.feature.admissions

import com.littlebridge.enrollplus.core.created
import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.okMessage
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.AdmissionEnquiriesTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Serializable
data class EnquiryDto(
    val id: String? = null,
    @SerialName("student_name") val studentName: String,
    @SerialName("parent_name") val parentName: String,
    @SerialName("class") val className: String,
    val date: String,
    val status: String,
    @SerialName("profile_pic") val profilePic: String? = null
)

@Serializable
data class EnquirySummaryCount(
    val total: Int,
    val new: Int,
    @SerialName("follow_ups") val followUps: Int,
    val converted: Int
)

@Serializable
data class EnquirySummaryResponse(
    @SerialName("summary_count") val summaryCount: EnquirySummaryCount,
    @SerialName("recent_enquiries") val recentEnquiries: List<EnquiryDto>,
    val efficiency: String
)

@Serializable
data class PaginationDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("total_pages") val totalPages: Int,
    @SerialName("total_records") val totalRecords: Int
)

@Serializable
data class EnquiryListResponse(val enquiries: List<EnquiryDto>, val pagination: PaginationDto)

@Serializable
data class CreateEnquiryDto(
    @SerialName("student_name") val studentName: String,
    @SerialName("parent_name") val parentName: String,
    @SerialName("parent_phone") val parentPhone: String? = null,
    @SerialName("parent_email") val parentEmail: String? = null,
    @SerialName("class") val className: String,
    val source: String? = null,
    val notes: String? = null,
    @SerialName("school_id") val schoolId: String? = null
)

@Serializable
data class UpdateStatusDto(val status: String)

private fun org.jetbrains.exposed.sql.ResultRow.toEnquiryDto() = EnquiryDto(
    id = this[AdmissionEnquiriesTable.id].value.toString(),
    studentName = this[AdmissionEnquiriesTable.studentName],
    parentName  = this[AdmissionEnquiriesTable.parentName],
    className   = this[AdmissionEnquiriesTable.className],
    date        = this[AdmissionEnquiriesTable.date],
    status      = this[AdmissionEnquiriesTable.status],
    profilePic  = this[AdmissionEnquiriesTable.profilePic]
)

fun Route.admissionRouting() {
    authenticate("jwt") {
        route("/api/v1/admissions/enquiries") {

            // -------- summary --------
            get("/summary") {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId
                val data = dbQuery {
                    val rows = AdmissionEnquiriesTable.selectAll()
                        .where { AdmissionEnquiriesTable.schoolId eq schoolId }
                        .toList()
                    val total = rows.size
                    val newC = rows.count { it[AdmissionEnquiriesTable.status] == "new" }
                    val foll = rows.count { it[AdmissionEnquiriesTable.status] == "followup" }
                    val conv = rows.count { it[AdmissionEnquiriesTable.status] == "converted" }
                    val recent = rows
                        .sortedByDescending { it[AdmissionEnquiriesTable.date] }
                        .take(5).map { it.toEnquiryDto() }
                    val denom = conv + foll
                    val eff = if (denom == 0) "0%" else "${conv * 100 / denom}%"
                    EnquirySummaryResponse(
                        summaryCount = EnquirySummaryCount(total, newC, foll, conv),
                        recentEnquiries = recent,
                        efficiency = eff
                    )
                }
                call.ok(data, message = "Enquiry summary fetched successfully")
            }

            // -------- list (paginated) --------
            get {
                val ctx = call.requireSchoolContext() ?: return@get
                val schoolId = ctx.schoolId
                val page  = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
                val offset = ((page - 1) * limit).toLong()

                val data = dbQuery {
                    val total = AdmissionEnquiriesTable.selectAll()
                        .where { AdmissionEnquiriesTable.schoolId eq schoolId }
                        .count().toInt()
                    val list = AdmissionEnquiriesTable.selectAll()
                        .where { AdmissionEnquiriesTable.schoolId eq schoolId }
                        .orderBy(AdmissionEnquiriesTable.date, SortOrder.DESC)
                        .limit(limit, offset)
                        .map { it.toEnquiryDto() }
                    val totalPages = if (total == 0) 0 else (total + limit - 1) / limit
                    EnquiryListResponse(list, PaginationDto(page, totalPages, total))
                }
                call.ok(data, message = "Enquiries fetched")
            }

            // -------- create --------
            post {
                val ctx = call.requireSchoolContext() ?: return@post
                val req = call.receive<CreateEnquiryDto>()
                // Always bind the enquiry to the caller's own school. A body-
                // supplied school_id is ignored so a school can't write into
                // another school's pipeline.
                val schoolId = ctx.schoolId
                val uid = ctx.userId
                val now = Instant.now()
                val newId = UUID.randomUUID()
                dbQuery {
                    AdmissionEnquiriesTable.insert {
                        it[AdmissionEnquiriesTable.id] = newId
                        it[AdmissionEnquiriesTable.schoolId] = schoolId
                        it[studentName] = req.studentName
                        it[parentName] = req.parentName
                        it[parentPhone] = req.parentPhone
                        it[parentEmail] = req.parentEmail
                        it[className] = req.className
                        it[date] = LocalDate.now().toString()
                        it[status] = "new"
                        it[admissionSource] = req.source
                        it[notes] = req.notes
                        it[assignedTo] = uid
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }
                call.created(
                    EnquiryDto(
                        id = newId.toString(),
                        studentName = req.studentName,
                        parentName = req.parentName,
                        className = req.className,
                        date = LocalDate.now().toString(),
                        status = "new"
                    ),
                    message = "Enquiry created"
                )
            }

            // -------- patch status --------
            patch("/{id}/status") {
                val ctx = call.requireSchoolContext() ?: return@patch
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run { call.fail("Invalid id"); return@patch }
                val req = call.receive<UpdateStatusDto>()
                if (req.status !in setOf("new", "followup", "converted", "rejected")) {
                    call.fail("Invalid status. Allowed: new|followup|converted|rejected"); return@patch
                }
                // Ownership enforced in the WHERE clause: the row must belong to
                // the caller's school. A foreign id matches 0 rows -> 404.
                val n = dbQuery {
                    AdmissionEnquiriesTable.update({
                        (AdmissionEnquiriesTable.id eq id) and
                            (AdmissionEnquiriesTable.schoolId eq ctx.schoolId)
                    }) {
                        it[status] = req.status
                        it[updatedAt] = Instant.now()
                        if (req.status == "converted") it[convertedAt] = Instant.now()
                    }
                }
                if (n == 0) call.fail("Enquiry not found", HttpStatusCode.NotFound)
                else call.okMessage("Enquiry status updated")
            }
        }
    }
}
