/*
 * File: SchoolProfileRouting.kt
 * Module: feature.school
 *
 * RA-47: institutional-profile EDIT. Before this, the core `schools` row
 * (name / board / medium / contact / principal / address / branding) could
 * only ever be written during onboarding (OnboardingRouting.syncSchoolBasics).
 * The admin Settings screen showed institutional info read-only with no way to
 * correct a typo or update the principal — post-onboarding it was frozen.
 *
 * Endpoints (JWT + school-scoped):
 *   GET /api/v1/school/profile   read the caller's school institutional row
 *   PUT /api/v1/school/profile   update it (school-admin only, RA-39)
 *
 * Multi-tenancy: the school is resolved from the JWT via requireSchoolContext /
 * requireSchoolAdmin (never from the body); the UPDATE is constrained to
 * ctx.schoolId, so an admin can only ever read/mutate their OWN school.
 *
 * PATCH semantics: every field on the request DTO is nullable; only non-null
 * fields are written, so a partial body never blanks unspecified columns.
 *
 * RA-47b: the editable surface now ALSO covers the geo coordinates
 * (latitude/longitude) and the richer institutional columns the onboarding
 * wizard can collect — school_type, affiliation_number, year_established,
 * website, total_students, total_classes, academic_year_start_month,
 * grading_system. These already existed on `schools` but had no read/write
 * path post-onboarding, so an admin who left them blank during onboarding
 * could never fill them in. They are surfaced in both GET and PUT here and
 * persist straight to Supabase.
 */
package com.littlebridge.enrollplus.feature.school

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.requireSchoolAdmin
import com.littlebridge.enrollplus.core.requireSchoolContext
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.SchoolsTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant

@Serializable
data class SchoolProfileDto(
    val id: String,
    val name: String,
    val board: String,
    val medium: String,
    @SerialName("school_gender") val schoolGender: String,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("principal_name") val principalName: String? = null,
    @SerialName("principal_phone") val principalPhone: String? = null,
    @SerialName("principal_email") val principalEmail: String? = null,
    @SerialName("full_address") val fullAddress: String? = null,
    val city: String,
    val district: String,
    val state: String,
    val pincode: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("brand_color") val brandColor: String,
    // RA-47b: geo + richer institutional fields the onboarding wizard collects.
    // They live on `schools` but were previously read-only/unfillable post-
    // onboarding — surfaced here so an admin who left them blank can complete
    // them later from Settings and have it persist to Supabase.
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("school_type") val schoolType: String? = null,
    @SerialName("affiliation_number") val affiliationNumber: String? = null,
    @SerialName("year_established") val yearEstablished: Int? = null,
    val website: String? = null,
    @SerialName("total_students") val totalStudents: Int? = null,
    @SerialName("total_classes") val totalClasses: Int? = null,
    @SerialName("academic_year_start_month") val academicYearStartMonth: String? = null,
    @SerialName("grading_system") val gradingSystem: String? = null
)

/**
 * Update payload. Every field nullable → PATCH semantics: null means "leave as
 * is". Blank strings ARE written for nullable columns (the admin can clear a
 * contact), but the three NOT-NULL columns (name/city/district) are only
 * written when a non-blank value is supplied so the row can never be corrupted.
 */
@Serializable
data class UpdateSchoolProfileRequest(
    val name: String? = null,
    val board: String? = null,
    val medium: String? = null,
    @SerialName("school_gender") val schoolGender: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("principal_name") val principalName: String? = null,
    @SerialName("principal_phone") val principalPhone: String? = null,
    @SerialName("principal_email") val principalEmail: String? = null,
    @SerialName("full_address") val fullAddress: String? = null,
    val city: String? = null,
    val district: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("brand_color") val brandColor: String? = null,
    // Geo + richer institutional fields (all nullable → PATCH semantics). These
    // map to nullable columns, so a supplied blank string clears them and a null
    // leaves them untouched. Numeric fields are written as-is when present.
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("school_type") val schoolType: String? = null,
    @SerialName("affiliation_number") val affiliationNumber: String? = null,
    @SerialName("year_established") val yearEstablished: Int? = null,
    val website: String? = null,
    @SerialName("total_students") val totalStudents: Int? = null,
    @SerialName("total_classes") val totalClasses: Int? = null,
    @SerialName("academic_year_start_month") val academicYearStartMonth: String? = null,
    @SerialName("grading_system") val gradingSystem: String? = null
)

private fun rowToDto(row: org.jetbrains.exposed.sql.ResultRow): SchoolProfileDto =
    SchoolProfileDto(
        id = row[SchoolsTable.id].value.toString(),
        name = row[SchoolsTable.name],
        board = row[SchoolsTable.board],
        medium = row[SchoolsTable.medium],
        schoolGender = row[SchoolsTable.schoolGender],
        contactPhone = row[SchoolsTable.contactPhone],
        contactEmail = row[SchoolsTable.contactEmail],
        principalName = row[SchoolsTable.principalName],
        principalPhone = row[SchoolsTable.principalPhone],
        principalEmail = row[SchoolsTable.principalEmail],
        fullAddress = row[SchoolsTable.fullAddress],
        city = row[SchoolsTable.city],
        district = row[SchoolsTable.district],
        state = row[SchoolsTable.state],
        pincode = row[SchoolsTable.pincode],
        logoUrl = row[SchoolsTable.logoUrl],
        brandColor = row[SchoolsTable.brandColor],
        latitude = row[SchoolsTable.latitude],
        longitude = row[SchoolsTable.longitude],
        schoolType = row[SchoolsTable.schoolType],
        affiliationNumber = row[SchoolsTable.affiliationNumber],
        yearEstablished = row[SchoolsTable.yearEstablished],
        website = row[SchoolsTable.website],
        totalStudents = row[SchoolsTable.totalStudents],
        totalClasses = row[SchoolsTable.totalClasses],
        academicYearStartMonth = row[SchoolsTable.academicYearStartMonth],
        gradingSystem = row[SchoolsTable.gradingSystem]
    )

fun Route.schoolProfileRouting() {
    authenticate("jwt") {
        route("/api/v1/school/profile") {

            // ---- read the caller's school institutional row ----
            get {
                val ctx = call.requireSchoolContext() ?: return@get
                val dto = dbQuery {
                    SchoolsTable.selectAll()
                        .where { SchoolsTable.id eq ctx.schoolId }
                        .firstOrNull()
                        ?.let(::rowToDto)
                }
                if (dto == null) {
                    call.fail("School not found", HttpStatusCode.NotFound, "SCHOOL_NOT_FOUND")
                    return@get
                }
                call.ok(dto, message = "School profile fetched")
            }

            // ---- update it (privileged: school-admin only, RA-39) ----
            put {
                val ctx = call.requireSchoolAdmin() ?: return@put
                val req = runCatching { call.receive<UpdateSchoolProfileRequest>() }.getOrNull()
                    ?: run { call.fail("Invalid body"); return@put }

                val dto = dbQuery {
                    val exists = SchoolsTable.selectAll()
                        .where { SchoolsTable.id eq ctx.schoolId }
                        .firstOrNull() ?: return@dbQuery null

                    SchoolsTable.update({ SchoolsTable.id eq ctx.schoolId }) {
                        // NOT-NULL columns: only write when non-blank.
                        req.name?.takeIf { v -> v.isNotBlank() }?.let { v -> it[name] = v }
                        req.board?.takeIf { v -> v.isNotBlank() }?.let { v -> it[board] = v }
                        req.medium?.takeIf { v -> v.isNotBlank() }?.let { v -> it[medium] = v }
                        req.schoolGender?.takeIf { v -> v.isNotBlank() }?.let { v -> it[schoolGender] = v }
                        req.city?.takeIf { v -> v.isNotBlank() }?.let { v -> it[city] = v }
                        req.district?.takeIf { v -> v.isNotBlank() }?.let { v -> it[district] = v }
                        req.state?.takeIf { v -> v.isNotBlank() }?.let { v -> it[state] = v }
                        req.brandColor?.takeIf { v -> v.isNotBlank() }?.let { v -> it[brandColor] = v }
                        // Nullable columns: write whatever is supplied (incl. blank → clear).
                        req.contactPhone?.let { v -> it[contactPhone] = v.ifBlank { null } }
                        req.contactEmail?.let { v -> it[contactEmail] = v.ifBlank { null } }
                        req.principalName?.let { v -> it[principalName] = v.ifBlank { null } }
                        req.principalPhone?.let { v -> it[principalPhone] = v.ifBlank { null } }
                        req.principalEmail?.let { v -> it[principalEmail] = v.ifBlank { null } }
                        req.fullAddress?.let { v -> it[fullAddress] = v.ifBlank { null } }
                        req.pincode?.let { v -> it[pincode] = v.ifBlank { null } }
                        req.logoUrl?.let { v -> it[logoUrl] = v.ifBlank { null } }
                        // Geo + richer institutional columns (all nullable).
                        // Numeric coords/values are written whenever supplied;
                        // text fields treat a blank string as "clear".
                        req.latitude?.let { v -> it[latitude] = v }
                        req.longitude?.let { v -> it[longitude] = v }
                        req.schoolType?.let { v -> it[schoolType] = v.ifBlank { null } }
                        req.affiliationNumber?.let { v -> it[affiliationNumber] = v.ifBlank { null } }
                        req.yearEstablished?.let { v -> it[yearEstablished] = v }
                        req.website?.let { v -> it[website] = v.ifBlank { null } }
                        req.totalStudents?.let { v -> it[totalStudents] = v }
                        req.totalClasses?.let { v -> it[totalClasses] = v }
                        req.academicYearStartMonth?.let { v -> it[academicYearStartMonth] = v.ifBlank { null } }
                        req.gradingSystem?.let { v -> it[gradingSystem] = v.ifBlank { null } }
                        it[updatedAt] = Instant.now()
                    }

                    SchoolsTable.selectAll()
                        .where { SchoolsTable.id eq ctx.schoolId }
                        .first()
                        .let(::rowToDto)
                }

                if (dto == null) {
                    call.fail("School not found", HttpStatusCode.NotFound, "SCHOOL_NOT_FOUND")
                    return@put
                }
                call.ok(dto, message = "School profile updated")
            }
        }
    }
}
