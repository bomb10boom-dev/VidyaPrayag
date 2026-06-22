package com.littlebridge.enrollplus.feature.admin.domain.model

import kotlinx.serialization.Serializable

/**
 * UI-facing representation of a single onboarding step on the SchoolDashboard.
 *
 * This is built from the server's `OnboardingStepData` (returned by
 * `GET /api/v1/user/details`) and contains everything the dashboard needs to
 * render the row + decide where the "Start / Continue" CTA should jump to.
 *
 * @property id Sequential 1-based step number.
 * @property serverKey The canonical step identifier the rest of the system
 *   knows: one of [SERVER_KEY_BASIC], [SERVER_KEY_BRANDING],
 *   [SERVER_KEY_ACADEMIC], [SERVER_KEY_REVIEW]. Used by the dashboard to map
 *   a step to its corresponding `Destination`.
 * @property title Display title (e.g. "Institutional Basics").
 * @property description Short helper text.
 * @property status One of [STATUS_COMPLETED], [STATUS_PENDING], [STATUS_LOCKED].
 *   Drives the trailing indicator (filled green check / hollow dark / hollow grey).
 * @property iconUrl Optional remote icon. We deliberately ignore the Google
 *   `lh3.googleusercontent.com/aida/...` URLs the server currently returns
 *   for `icon` — those are private and 403 from the device. The dashboard
 *   falls back to a local Material icon when this is null.
 * @property isEnabled Mirrors the server's `is_enabled`. False for LOCKED steps.
 */
@Serializable
data class OnboardingStep(
    val id: Int,
    val serverKey: String = SERVER_KEY_BASIC,
    val title: String,
    val description: String,
    val status: String = STATUS_PENDING,
    val iconUrl: String? = null,
    val isEnabled: Boolean = true
) {
    /** Convenience: true iff [status] == [STATUS_COMPLETED]. Kept for the
     *  existing dashboard row rendering. */
    val isCompleted: Boolean
        get() = status.equals(STATUS_COMPLETED, ignoreCase = true)

    companion object {
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_LOCKED = "LOCKED"

        const val SERVER_KEY_BASIC = "BASIC"
        const val SERVER_KEY_BRANDING = "BRANDING"
        const val SERVER_KEY_ACADEMIC = "ACADEMIC"
        const val SERVER_KEY_REVIEW = "REVIEW"
    }
}
