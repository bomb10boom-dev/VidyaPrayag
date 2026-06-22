package com.littlebridge.enrollplus

import com.littlebridge.enrollplus.feature.onboarding.deriveOnboardingStatus
import com.littlebridge.enrollplus.feature.onboarding.parseOnboardingLedger
import com.littlebridge.enrollplus.feature.onboarding.resumeStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression guard for the "fresh school is redirected to Step 3 instead of
 * Step 1" onboarding bug.
 *
 * ROOT CAUSE: `POST /auth/register-school` pre-creates a fully-named `schools`
 * row at signup. The old status logic inferred "BASIC step done" purely from
 * "a named school row exists", so a brand-new school read as having already
 * finished Step 1 and the gate resumed the wizard at ACADEMIC (frontend Step 3).
 *
 * THE FIX: per-step completion is now driven by `schools.onboarding_steps_done`
 * — an explicit ledger written ONLY when the admin actually submits a wizard
 * step — combined with substantive persisted data (classes / a valid stamp).
 * A registration placeholder has an empty ledger and no classes, so it resumes
 * at BASIC.
 *
 * These tests pin the PURE derivation (`deriveOnboardingStatus` + `resumeStep`),
 * which is the single source of truth shared by `GET /onboarding/status`, the
 * school dashboard, and user details. They cover every scenario in the task's
 * End-to-End Validation matrix.
 */
class OnboardingStatusTest {

    // ── Scenario 1: Fresh school signup → resume at BASIC (Step 1) ───────────
    @Test
    fun freshlyRegisteredSchool_resumesAtBasic() {
        // What register-school leaves behind: a school row exists, but NO ledger,
        // NO classes, NO completion stamp. (logoPresent=false: brand color is the
        // default, no logo uploaded.)
        val status = deriveOnboardingStatus(
            schoolExists = true,
            ledger = parseOnboardingLedger(null),
            hasClasses = false,
            logoPresent = false,
            stampPresent = false,
        )
        assertFalse(status.basicsDone, "Fresh school must NOT have BASIC marked done")
        assertFalse(status.brandingDone)
        assertFalse(status.academicDone)
        assertFalse(status.finalDone)
        assertEquals(0, status.completedSteps)
        assertEquals("BASIC", status.resumeStep(), "Fresh school must resume at Step 1 (BASIC)")
    }

    // ── Scenario 2: Partial Step 1 data (drafts only, no submit) → stay on BASIC
    @Test
    fun partialStep1_withoutSubmit_staysOnBasic() {
        // A half-typed BASIC form saves drafts but the ledger is only written on
        // a successful submit. No ledger + no classes ⇒ still BASIC.
        val status = deriveOnboardingStatus(
            schoolExists = true,
            ledger = emptySet(),
            hasClasses = false,
            logoPresent = false,
            stampPresent = false,
        )
        assertEquals("BASIC", status.resumeStep())
        assertFalse(status.basicsDone)
    }

    // ── Scenario 3: Completed Step 1 only → resume at BRANDING (Step 2) ───────
    @Test
    fun completedBasicOnly_resumesAtBranding() {
        val status = deriveOnboardingStatus(
            schoolExists = true,
            ledger = setOf("BASIC"),
            hasClasses = false,
            logoPresent = false,
            stampPresent = false,
        )
        assertTrue(status.basicsDone)
        assertFalse(status.brandingDone)
        assertEquals("BRANDING", status.resumeStep())
    }

    // ── Scenario 4: Completed Steps 1 & 2 → resume at ACADEMIC (Step 3) ───────
    @Test
    fun completedBasicAndBranding_resumesAtAcademic() {
        val status = deriveOnboardingStatus(
            schoolExists = true,
            ledger = setOf("BASIC", "BRANDING"),
            hasClasses = false,
            logoPresent = false,
            stampPresent = false,
        )
        assertTrue(status.basicsDone)
        assertTrue(status.brandingDone)
        assertFalse(status.academicDone)
        assertEquals("ACADEMIC", status.resumeStep())
    }

    // ── Scenario 5: Completed Steps 1, 2 & 3 → resume at REVIEW (Step 4) ──────
    @Test
    fun completedThroughAcademic_resumesAtReview() {
        val status = deriveOnboardingStatus(
            schoolExists = true,
            ledger = setOf("BASIC", "BRANDING", "ACADEMIC"),
            hasClasses = true, // ACADEMIC genuinely created classes
            logoPresent = false,
            stampPresent = false,
        )
        assertTrue(status.basicsDone)
        assertTrue(status.brandingDone)
        assertTrue(status.academicDone)
        assertFalse(status.finalDone, "REVIEW not done until the final stamp")
        assertEquals("REVIEW", status.resumeStep())
    }

    // ── Scenario 6: Fully completed onboarding → complete ────────────────────
    @Test
    fun fullyCompletedOnboarding_isComplete() {
        val status = deriveOnboardingStatus(
            schoolExists = true,
            ledger = setOf("BASIC", "BRANDING", "ACADEMIC", "REVIEW"),
            hasClasses = true,
            logoPresent = true,
            stampPresent = true,
        )
        assertTrue(status.basicsDone)
        assertTrue(status.academicDone)
        assertTrue(status.finalDone)
        assertEquals(4, status.completedSteps)
        assertEquals(1.0, status.progress)
        assertEquals("COMPLETED", status.overallStatus)
    }

    // ── Defensive: a STALE onboarded_at stamp on an empty row must NOT complete
    @Test
    fun staleStampWithoutSubstantiveData_isNotComplete() {
        // A hand-set / seeded `onboarded_at` on a school with no classes and no
        // ledger must NOT report onboarding complete — it should send the admin
        // back to BASIC instead of an empty dashboard.
        val status = deriveOnboardingStatus(
            schoolExists = true,
            ledger = emptySet(),
            hasClasses = false,
            logoPresent = false,
            stampPresent = true, // stale stamp
        )
        assertFalse(status.finalDone, "Bare stamp on an empty row must NOT complete onboarding")
        assertFalse(status.basicsDone)
        assertEquals("BASIC", status.resumeStep())
    }

    // ── Defensive: legacy row with real classes but no ledger still resolves ──
    @Test
    fun legacyRowWithClasses_butNoLedger_resolvesForward() {
        // Rows created before the ledger existed: real classes imply the admin
        // did go through BASIC/BRANDING/ACADEMIC, so the flow resolves forward
        // (resume at REVIEW) instead of wrongly restarting at BASIC.
        val status = deriveOnboardingStatus(
            schoolExists = true,
            ledger = emptySet(),
            hasClasses = true,
            logoPresent = false,
            stampPresent = false,
        )
        assertTrue(status.basicsDone)
        assertTrue(status.brandingDone)
        assertTrue(status.academicDone)
        assertFalse(status.finalDone)
        assertEquals("REVIEW", status.resumeStep())
    }

    // ── No school at all → empty status, resume at BASIC ─────────────────────
    @Test
    fun noSchool_resumesAtBasic() {
        val status = deriveOnboardingStatus(
            schoolExists = false,
            ledger = emptySet(),
            hasClasses = false,
            logoPresent = false,
            stampPresent = false,
        )
        assertFalse(status.schoolExists)
        assertEquals(0, status.completedSteps)
        assertEquals("BASIC", status.resumeStep())
    }

    // ── Ledger parsing is tolerant of whitespace / casing / junk ─────────────
    @Test
    fun ledgerParsing_isTolerant() {
        assertEquals(setOf("BASIC", "BRANDING"), parseOnboardingLedger(" basic , Branding "))
        assertEquals(emptySet(), parseOnboardingLedger(null))
        assertEquals(emptySet(), parseOnboardingLedger(""))
        assertEquals(setOf("ACADEMIC"), parseOnboardingLedger("ACADEMIC,bogus,"))
    }
}
