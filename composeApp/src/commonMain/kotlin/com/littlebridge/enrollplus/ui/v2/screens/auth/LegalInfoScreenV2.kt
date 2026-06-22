package com.littlebridge.enrollplus.ui.v2.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VLabel
import com.littlebridge.enrollplus.ui.v2.components.VTopTabs
import com.littlebridge.enrollplus.ui.v2.theme.VPortalTone
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored

/**
 * The single point of truth for the user-facing support address. Surfaced on the Help Desk tab and
 * used to build the `mailto:` intent. Keep in sync with the footer copy on [CommonLandingScreenV2].
 */
const val SUPPORT_EMAIL: String = "littlebridge.team@gmail.com"

/** Which legal/info document the screen opens on. */
enum class LegalDoc { Privacy, Terms, Help }

/**
 * LegalInfoScreenV2 — the public Privacy Policy / Terms of Service / Help Desk surface.
 *
 * Reached from the landing footer ("Privacy Policy", "Terms of Service", "Help Desk") and from the
 * "Terms & Privacy Policy" continue-footnote. A single screen with a [VTopTabs] switcher so the
 * three documents share one back-stack entry. Rendered entirely in the V* system (VColors tokens,
 * VType, VDimens) — Light tone, matching the unauthenticated funnel.
 *
 * The copy is intentionally **minimal and honest** for the current phase: it states what the app
 * actually does (school-scoped data, JWT auth, no third-party ad tracking) without fabricating
 * certifications. The Help Desk tab is the live support channel — a tap on the email opens the
 * device mail composer via [LocalUriHandler].
 */
@Composable
fun LegalInfoScreenV2(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initial: LegalDoc = LegalDoc.Privacy,
) = VTheme(tone = VPortalTone.Light) {
    val c = VTheme.colors
    val d = VTheme.dimens

    val tabs = listOf("Privacy", "Terms", "Help Desk")
    var selected by remember {
        mutableStateOf(
            when (initial) {
                LegalDoc.Privacy -> "Privacy"
                LegalDoc.Terms -> "Terms"
                LegalDoc.Help -> "Help Desk"
            },
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Header: back chevron + title ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BackChip(onClick = onBack)
            Text(
                "Legal & Support",
                style = VTheme.type.h4.colored(c.ink).copy(fontWeight = FontWeight.Bold),
            )
        }

        VTopTabs(tabs = tabs, selected = selected, onSelect = { selected = it })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            when (selected) {
                "Privacy" -> PrivacyContent()
                "Terms" -> TermsContent()
                else -> HelpDeskContent()
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "VidyaSetu · Little Bridge",
                style = VTheme.type.caption.colored(c.ink3),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(d.lg))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Documents — minimal, honest, current-phase copy.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PrivacyContent() {
    DocHeader(icon = VIcons.ShieldCheck, eyebrow = "Your data", title = "Privacy Policy")
    LastUpdated()
    Para(
        "VidyaSetu connects parents and schools. We collect only the information needed to run the " +
            "service: your name and contact details, the school and children you are linked to, and " +
            "the academic records (attendance, marks, fees, messages) your school shares with you.",
    )
    SectionTitle("What we collect")
    Bullet("Account details — name, phone or email used to sign in.")
    Bullet("School linkage — the institution and student(s) connected to your account.")
    Bullet("Academic data — attendance, assessments, fees and announcements published by your school.")
    Bullet("Messages — communication you send or receive through the in-app channels.")

    SectionTitle("How we use it")
    Bullet("To show you your child's academic information and school updates.")
    Bullet("To deliver notifications you have opted into (results, fees, announcements).")
    Bullet("To keep your account secure and prevent misuse.")

    SectionTitle("What we never do")
    Bullet("We do not sell your data.")
    Bullet("We do not use your data for third-party advertising.")
    Bullet("We do not share your child's records outside your school's authorised staff and your linked parent account.")

    SectionTitle("Your data is school-scoped")
    Para(
        "Every record is tied to your school. Access is decided server-side from your signed-in " +
            "session — you only ever see the data belonging to your own account and school.",
    )

    SectionTitle("Retention & deletion")
    Para(
        "We keep your data while your account is active. To request access, correction, or deletion " +
            "of your information, contact us at the Help Desk — we respond to every request.",
    )
}

@Composable
private fun TermsContent() {
    DocHeader(icon = VIcons.FileText, eyebrow = "The agreement", title = "Terms of Service")
    LastUpdated()
    Para(
        "By using VidyaSetu you agree to these terms. They are written to be clear and fair. If you " +
            "do not agree, please do not use the app.",
    )
    SectionTitle("Using the app")
    Bullet("You must provide accurate information when creating your account.")
    Bullet("You are responsible for keeping your login credentials confidential.")
    Bullet("Use the app only for its intended purpose — connecting with your school and tracking your child's progress.")

    SectionTitle("Accounts & access")
    Para(
        "Parent accounts link to children enrolled at a participating school. Teacher and staff " +
            "accounts are created by your school's administrator. Schools control which records are " +
            "published to parents.",
    )

    SectionTitle("Content & communication")
    Bullet("Messages and announcements are part of the official school record.")
    Bullet("Do not post unlawful, abusive, or misleading content.")
    Bullet("We may suspend accounts that violate these terms or misuse the platform.")

    SectionTitle("Availability")
    Para(
        "We work hard to keep VidyaSetu running, but the service is provided \"as is\". We are not " +
            "liable for occasional downtime, and we may update features as the product evolves.",
    )

    SectionTitle("Changes to these terms")
    Para(
        "We may update these terms as the app grows. We will surface material changes in the app. " +
            "Continued use after an update means you accept the revised terms.",
    )

    SectionTitle("Contact")
    Para("Questions about these terms? Reach us via the Help Desk tab.")
}

@Composable
private fun HelpDeskContent() {
    val c = VTheme.colors
    val uriHandler = LocalUriHandler.current

    DocHeader(icon = VIcons.Chat, eyebrow = "We're here", title = "Help Desk")
    Para(
        "Need a hand, found a bug, or have a question about your account? Our team reads every " +
            "message and replies as quickly as we can.",
    )
    Spacer(Modifier.height(16.dp))

    // Primary contact card — taps open the device mail composer.
    VCard(
        onClick = {
            runCatching {
                uriHandler.openUri("mailto:$SUPPORT_EMAIL?subject=VidyaSetu%20Support")
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c.teal.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(VIcons.Mail, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Email support",
                    style = VTheme.type.bodyStrong.colored(c.ink).copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(2.dp))
                Text(SUPPORT_EMAIL, style = VTheme.type.caption.colored(c.tealDeep))
            }
            Icon(VIcons.ChevronRight, contentDescription = null, tint = c.ink3, modifier = Modifier.size(20.dp))
        }
    }

    Spacer(Modifier.height(20.dp))
    SectionTitle("What to include")
    Bullet("Your role (parent, teacher, or admin) and your school's name.")
    Bullet("A short description of the problem or question.")
    Bullet("A screenshot, if it helps explain the issue.")

    Spacer(Modifier.height(20.dp))
    SectionTitle("Common questions")
    FaqRow(
        "I can't link my child",
        "Check the student code with your school's office, then try the Link Child flow again from your profile.",
    )
    FaqRow(
        "I forgot my password",
        "Teachers and admins can ask their school administrator to reset it. Parents sign in with a one-time code.",
    )
    FaqRow(
        "I'm not getting notifications",
        "Make sure notifications are enabled for VidyaSetu in your device settings.",
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Small building blocks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BackChip(onClick: () -> Unit) {
    val c = VTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(c.card)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(VIcons.ArrowLeft, contentDescription = "Back", tint = c.ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DocHeader(icon: ImageVector, eyebrow: String, title: String) {
    val c = VTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.lavender.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.navy, modifier = Modifier.size(22.dp))
        }
        Column {
            VLabel(eyebrow)
            Spacer(Modifier.height(2.dp))
            Text(title, style = VTheme.type.h2.colored(c.ink).copy(fontWeight = FontWeight.Bold))
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun LastUpdated() {
    val c = VTheme.colors
    Text(
        "Last updated: June 2026",
        style = VTheme.type.caption.colored(c.ink3),
    )
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(
        text,
        style = VTheme.type.h4.colored(VTheme.colors.ink).copy(fontWeight = FontWeight.Bold),
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Para(text: String) {
    Text(
        text,
        style = VTheme.type.body.colored(VTheme.colors.ink2),
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Bullet(text: String) {
    val c = VTheme.colors
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(c.tealDeep),
        )
        Text(text, style = VTheme.type.body.colored(c.ink2), modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun FaqRow(question: String, answer: String) {
    val c = VTheme.colors
    Spacer(Modifier.height(10.dp))
    VCard(border = true, elevated = false, background = c.card) {
        Text(question, style = VTheme.type.bodyStrong.colored(c.ink).copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(4.dp))
        Text(answer, style = VTheme.type.caption.colored(c.ink2))
    }
}
