/*
 * File: NotifyRecipients.kt
 * Module: feature.notifications
 *
 * RA-41: recipient resolvers for the notification spine. Triggers (attendance,
 * marks, homework, announcements, …) know WHAT happened in WHICH school but need
 * to turn that into the set of parent / teacher app_users.id to notify. These
 * helpers do that, ALWAYS scoped to a single school_id (multi-tenant isolation).
 *
 * Parent linkage: children.parent_id (app_users.id) joined by either
 *   - children.student_code  (exact student → that student's parents), or
 *   - children.current_grade (class → all parents whose child is in that class).
 */
package com.littlebridge.enrollplus.feature.notifications

import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.ChildrenTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.TeacherSubjectAssignmentsTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

object NotifyRecipients {

    /** Parent app_users.id for a specific student_code within [schoolId]. */
    suspend fun parentsOfStudent(schoolId: UUID, studentCode: String): List<UUID> = dbQuery {
        ChildrenTable.selectAll().where {
            (ChildrenTable.schoolId eq schoolId) and
                (ChildrenTable.studentCode eq studentCode) and
                (ChildrenTable.isActive eq true)
        }.map { it[ChildrenTable.parentId] }.distinct()
    }

    /**
     * Parent app_users.id for every child in [className] within [schoolId].
     * Used for class-wide events (homework, class announcement). children only
     * carries current_grade (no section), so this targets the whole grade.
     */
    suspend fun parentsOfClass(schoolId: UUID, className: String): List<UUID> = dbQuery {
        ChildrenTable.selectAll().where {
            (ChildrenTable.schoolId eq schoolId) and
                (ChildrenTable.currentGrade eq className) and
                (ChildrenTable.isActive eq true)
        }.map { it[ChildrenTable.parentId] }.distinct()
    }

    /** Every active parent app_users row with a child enrolled in [schoolId]. */
    suspend fun parentsInSchool(schoolId: UUID): List<UUID> = dbQuery {
        ChildrenTable.selectAll().where {
            (ChildrenTable.schoolId eq schoolId) and (ChildrenTable.isActive eq true)
        }.map { it[ChildrenTable.parentId] }.distinct()
    }

    /** Every active teacher app_users.id in [schoolId]. */
    suspend fun teachersInSchool(schoolId: UUID): List<UUID> = dbQuery {
        AppUsersTable.selectAll().where {
            (AppUsersTable.schoolId eq schoolId) and
                (AppUsersTable.role eq "teacher") and
                (AppUsersTable.isActive eq true)
        }.map { it[AppUsersTable.id].value }.distinct()
    }

    /** Every active school-admin app_users.id in [schoolId] (school_admin | admin). */
    suspend fun adminsInSchool(schoolId: UUID): List<UUID> = dbQuery {
        AppUsersTable.selectAll().where {
            (AppUsersTable.schoolId eq schoolId) and
                ((AppUsersTable.role eq "school_admin") or (AppUsersTable.role eq "admin")) and
                (AppUsersTable.isActive eq true)
        }.map { it[AppUsersTable.id].value }.distinct()
    }

    /**
     * RA-49: resolve the precise set of parent app_users.id for an announcement's
     * audience scope, so the IN-APP notification fan-out matches the WhatsApp
     * expansion (no blasting the whole school when a post targets one class).
     *
     * Mirrors AnnouncementRouting.resolveRecipientPhones key shapes:
     *   ALL_SCHOOL → every parent in school
     *   CLASS/SECTION → parents whose child's current_grade ∈ class_names
     *   SUBJECT → parents of children in any class teaching subjects[]
     *   STUDENT → parents linked to student_codes[]
     *   CUSTOM → ALL_SCHOOL (phone-only scope has no app_user mapping; fall back)
     * All scoped to [schoolId]. Always returns DISTINCT ids.
     */
    suspend fun parentsForAudience(
        schoolId: UUID,
        audienceType: String,
        classNames: List<String> = emptyList(),
        subjects: List<String> = emptyList(),
        studentCodes: List<String> = emptyList(),
    ): List<UUID> = dbQuery {
        when (audienceType.uppercase()) {
            "CLASS", "SECTION" -> {
                val wanted = classNames.map { it.trim().lowercase() }.toSet()
                if (wanted.isEmpty()) emptyList()
                else ChildrenTable.selectAll().where {
                    (ChildrenTable.schoolId eq schoolId) and (ChildrenTable.isActive eq true)
                }.filter { (it[ChildrenTable.currentGrade]?.trim()?.lowercase()) in wanted }
                    .map { it[ChildrenTable.parentId] }.distinct()
            }

            "SUBJECT" -> {
                val wanted = subjects.map { it.trim().lowercase() }.toSet()
                if (wanted.isEmpty()) return@dbQuery emptyList<UUID>()
                val classes = TeacherSubjectAssignmentsTable.selectAll().where {
                    (TeacherSubjectAssignmentsTable.schoolId eq schoolId) and
                        (TeacherSubjectAssignmentsTable.isActive eq true)
                }.filter { it[TeacherSubjectAssignmentsTable.subject].trim().lowercase() in wanted }
                    .map { it[TeacherSubjectAssignmentsTable.className].trim().lowercase() }
                    .toSet()
                if (classes.isEmpty()) emptyList()
                else ChildrenTable.selectAll().where {
                    (ChildrenTable.schoolId eq schoolId) and (ChildrenTable.isActive eq true)
                }.filter { (it[ChildrenTable.currentGrade]?.trim()?.lowercase()) in classes }
                    .map { it[ChildrenTable.parentId] }.distinct()
            }

            "STUDENT" -> {
                val wanted = studentCodes.map { it.trim() }.filter { it.isNotBlank() }.toSet()
                if (wanted.isEmpty()) emptyList()
                else ChildrenTable.selectAll().where {
                    (ChildrenTable.schoolId eq schoolId) and (ChildrenTable.isActive eq true)
                }.filter { it[ChildrenTable.studentCode]?.trim() in wanted }
                    .map { it[ChildrenTable.parentId] }.distinct()
            }

            // ALL_SCHOOL / CUSTOM / unknown → whole-school parents.
            else -> ChildrenTable.selectAll().where {
                (ChildrenTable.schoolId eq schoolId) and (ChildrenTable.isActive eq true)
            }.map { it[ChildrenTable.parentId] }.distinct()
        }
    }
}
