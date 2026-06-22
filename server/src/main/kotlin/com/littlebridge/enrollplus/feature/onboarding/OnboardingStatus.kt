/*
 * File: OnboardingStatus.kt
 * Module: feature.onboarding
 *
 * Purpose:
 *   THE single source of truth for "how far along is this school's onboarding".
 *   Before this, three places computed completion three different ways:
 *     - UserDetailsRouting : real school fields + school_classes count + onboarded_at  (correct)
 *     - SchoolDashboard    : "a draft row exists for this step"                          (too loose)
 *     - Onboarding submit  : "is_final_submission && step==REVIEW"                       (only the end)
 *
 *   Every caller now derives status from the SAME persisted facts, and — after
 *   the onboarding-redirect fix — from the EXPLICIT per-step completion ledger
 *   `schools.onboarding_steps_done`:
 *     Step 1 BASIC     -> "BASIC" in ledger (admin submitted Step 1) OR a legacy
 *                         row already has substantive wizard data (classes)
 *     Step 2 BRANDING  -> "BRANDING" in ledger OR substantive wizard data
 *     Step 3 ACADEMIC  -> >= 1 row in school_classes for this school
 *     Step 4 REVIEW    -> onboarded_at stamped AND BASIC + ACADEMIC genuinely done
 *
 *   THE BUG THIS FIXES: self-registration (`POST /auth/register-school`)
 *   pre-creates a fully-named `schools` row at signup. Deriving "BASIC done"
 *   from "a named school row exists" therefore marked a brand-new school's
 *   Step 1 complete, and the gate resumed the wizard at ACADEMIC (frontend
 *   Step 3), skipping the required earlier steps. Completion is now driven by
 *   the ledger (written ONLY when the admin submits a step) plus substantive
 *   data — never by a registration placeholder.
 *
 *   "Draft presence" is still NOT used for completion — drafts are only for
 *   restoring half-typed form state; the ledger is the authoritative signal.
 */
package com.littlebridge.enrollplus.feature.onboarding

import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.SchoolClassesTable
import com.littlebridge.enrollplus.db.SchoolsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

/** Per-step completion booleans derived strictly from persisted school data. */
data class OnboardingStatus(
    val schoolExists: Boolean,
    val basicsDone: Boolean,
    val brandingDone: Boolean,
    val academicDone: Boolean,
    val finalDone: Boolean
) {
    /** Number of completed steps (0..4). */
    val completedSteps: Int =
        listOf(basicsDone, brandingDone, academicDone, finalDone).count { it }

    val totalSteps: Int = 4

    val progress: Double = completedSteps.toDouble() / totalSteps.toDouble()

    /** Coarse overall status used by the home/dashboard surfaces. */
    val overallStatus: String = when {
        finalDone -> "COMPLETED"
        basicsDone || brandingDone || academicDone -> "IN_PROGRESS"
        else -> "NOT_STARTED"
    }

    /** True step-type given an index 1..4, used by the dashboard step list. */
    fun isStepDone(stepId: Int): Boolean = when (stepId) {
        1 -> basicsDone
        2 -> brandingDone
        3 -> academicDone
        4 -> finalDone
        else -> false
    }
}

/** Empty status for a user that hasn't created a school yet. */
private val EMPTY_STATUS = OnboardingStatus(
    schoolExists = false,
    basicsDone = false,
    brandingDone = false,
    academicDone = false,
    finalDone = false
)

/** Canonical wizard steps, in order. Shared by all onboarding surfaces. */
val ONBOARDING_STEPS: List<String> = listOf("BASIC", "BRANDING", "ACADEMIC", "REVIEW")

/** Parse the persisted CSV completion ledger into a normalised set of steps. */
fun parseOnboardingLedger(csv: String?): Set<String> =
    csv?.split(',')
        ?.map { it.trim().uppercase() }
        ?.filter { it in ONBOARDING_STEPS }
        ?.toSet()
        ?: emptySet()

/**
 * PURE onboarding-state derivation — the single, testable source of truth for
 * "how far along is this school's onboarding", shared by every surface
 * (`GET /onboarding/status`, the dashboard, user details).
 *
 * Inputs are the RAW persisted facts:
 *   - [schoolExists]   : a `schools` row is linked to the user.
 *   - [ledger]         : steps the admin ACTUALLY submitted in the wizard
 *                        (`schools.onboarding_steps_done`). Written ONLY by
 *                        `/onboarding/submit` + `/complete` — NEVER at
 *                        registration. This is what fixes the redirect bug:
 *                        a freshly self-registered school has an empty ledger,
 *                        so "a named school row exists" no longer marks BASIC
 *                        done and the wizard correctly resumes at Step 1.
 *   - [hasClasses]     : ≥1 row in `school_classes` (registration never creates
 *                        these → trustworthy independent signal for ACADEMIC,
 *                        and a defensive fallback for legacy pre-ledger rows).
 *   - [logoPresent]    : a branding logo exists (best-effort BRANDING signal).
 *   - [stampPresent]   : `schools.onboarded_at` is set.
 *
 * Defensive against bad/legacy data:
 *   - A bare `onboarded_at` stamp on an otherwise-empty row does NOT complete
 *     onboarding — REVIEW also requires BASIC + ACADEMIC to genuinely exist.
 *   - Legacy rows that pre-date the ledger still resolve correctly because real
 *     classes imply BASIC/BRANDING were done.
 */
fun deriveOnboardingStatus(
    schoolExists: Boolean,
    ledger: Set<String>,
    hasClasses: Boolean,
    logoPresent: Boolean,
    stampPresent: Boolean,
): OnboardingStatus {
    if (!schoolExists) return EMPTY_STATUS

    val academicDone = hasClasses
    // BASIC done only when the admin submitted Step 1 (ledger) OR a legacy row
    // already has substantive wizard data (classes). A registration-seeded name
    // + contact is intentionally NOT sufficient.
    val basicsDone = "BASIC" in ledger || academicDone
    val brandingDone = "BRANDING" in ledger || logoPresent || academicDone
    // REVIEW requires the completion stamp (or an explicit REVIEW ledger entry)
    // AND the substantive prerequisite data. A stale stamp alone never completes.
    val finalDone = ("REVIEW" in ledger || stampPresent) && basicsDone && academicDone

    return OnboardingStatus(
        schoolExists = true,
        basicsDone = basicsDone,
        brandingDone = brandingDone,
        academicDone = academicDone,
        finalDone = finalDone
    )
}

/**
 * First wizard step that is still incomplete, walking the steps in order. This
 * is the value the post-login gate uses to decide where to open the wizard, and
 * is what guarantees a fresh school starts at BASIC (Step 1) and only advances
 * once each prior step's required data genuinely exists.
 */
fun OnboardingStatus.resumeStep(): String = when {
    !basicsDone -> "BASIC"
    !brandingDone -> "BRANDING"
    !academicDone -> "ACADEMIC"
    !finalDone -> "REVIEW"
    else -> "REVIEW"
}

/**
 * Computes [OnboardingStatus] for the school owned by [userId]. Runs in its own
 * dbQuery; pass an already-open transaction-free UUID.
 */
suspend fun computeOnboardingStatus(userId: UUID): OnboardingStatus = dbQuery {
    val schoolId = AppUsersTable.selectAll()
        .where { AppUsersTable.id eq userId }
        .singleOrNull()
        ?.get(AppUsersTable.schoolId)
        ?: return@dbQuery EMPTY_STATUS

    val school = SchoolsTable.selectAll()
        .where { SchoolsTable.id eq schoolId }
        .singleOrNull()
        ?: return@dbQuery EMPTY_STATUS

    // Explicit per-step completion ledger (CSV of BASIC,BRANDING,ACADEMIC,REVIEW)
    // written ONLY when the admin actually submits a wizard step. Registration
    // never writes it, so a registration placeholder no longer falsely completes
    // BASIC. Legacy rows that pre-date the ledger fall back to substantive data.
    val ledger = parseOnboardingLedger(school[SchoolsTable.onboardingStepsDone])

    val hasClasses = SchoolClassesTable.selectAll()
        .where { SchoolClassesTable.schoolId eq schoolId }
        .count() > 0L

    deriveOnboardingStatus(
        schoolExists = true,
        ledger = ledger,
        hasClasses = hasClasses,
        logoPresent = school[SchoolsTable.logoUrl]?.isNotBlank() == true,
        stampPresent = school[SchoolsTable.onboardedAt] != null,
    )
}
