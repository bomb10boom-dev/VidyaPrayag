package com.littlebridge.enrollplus.ui.v2.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.collectAsState
import com.littlebridge.enrollplus.ui.v2.components.ShimmerBox
import com.littlebridge.enrollplus.ui.v2.components.VBrandLogo
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.theme.VPortalTone
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import vidyaprayag.composeapp.generated.resources.Res
import vidyaprayag.composeapp.generated.resources.landing_school_1
import vidyaprayag.composeapp.generated.resources.landing_school_2

/**
 * CommonLandingScreenV2 — the app's first interactive surface for users with no session.
 *
 * A complete premium rebuild (PHASE 7 rewrite). Where the legacy landing piled marketing
 * sections on top of each other, this is a single, confident, two-path screen built the way
 * Linear / Notion / Monzo / Razorpay onboarded before they were famous: typography does the
 * heavy lifting, colour is restrained to exactly two jobs (the primary CTA and the active tab),
 * and every block earns its place.
 *
 * Anatomy (top → bottom):
 *  1. Brand mark — the *same* [VBrandLogo] the splash draws, on the *same* teal field, so the
 *     splash → landing transition reads as one continuous motion (LAW: splash continuity).
 *  2. Hero headline — the largest the [VTheme.type] scale allows (`h1`), set tight, full weight.
 *  3. Two-tab switcher — "For Schools" / "For Parents" with a spring-driven pill indicator.
 *  4. Tab content — a contained real photograph + a feature list (icon · name · one-line copy).
 *     Switching tabs slides horizontally in the selected direction (crisp, physical, once).
 *  5. Sticky CTA dock — two buttons whose weight swaps with the active tab.
 *
 * Every feature line below is derived from a real screen in this codebase (DailyAttendance,
 * AdmissionsCrm, Analytics, Comms, Results, TeacherPerformance for schools; AttendanceCalendar,
 * Academics, Fees, Messages, Activity, Leave for parents). No invented capabilities.
 *
 * The screen keeps its established navigation contract: [onParent] → Parent OTP auth,
 * [onAdmin] → Admin credential auth (teachers sign in via the Admin path). [onLegal] opens the
 * public Legal & Support surface. The CMS/schools view models are accepted for source
 * compatibility with the call site but the surface is intentionally self-contained — a public
 * first screen must never be blocked on a network state.
 */
@Composable
fun CommonLandingScreenV2(
    onParent: () -> Unit,
    onAdmin: () -> Unit,
    modifier: Modifier = Modifier,
    onLegal: (LegalDoc) -> Unit = {},
) = VTheme(tone = VPortalTone.Light) {
    val c = VTheme.colors
    val d = VTheme.dimens

    // 0 = For Schools, 1 = For Parents.
    var tab by remember { mutableStateOf(0) }
    var prevTab by remember { mutableStateOf(0) }

    // Entry reveal — the whole content fades in and slides up 24dp once, continuing the splash
    // exit motion. Driven by two Animatables on a single LaunchedEffect(Unit) (plays once, settles).
    val enterAlpha = remember { Animatable(0f) }
    val enterY = remember { Animatable(24f) }
    val logoScale = remember { Animatable(0.88f) }
    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f)) }
        launch { enterAlpha.animateTo(1f, tween(400, easing = EaseOutCubic)) }
        launch { enterY.animateTo(0f, tween(400, easing = EaseOutCubic)) }
    }

    Box(modifier.fillMaxSize().background(c.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = d.maxContentWidth)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    alpha = enterAlpha.value
                    translationY = enterY.value * density
                },
        ) {
            // Scrolling content above the sticky dock.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // 1 ── Brand zone (splash-continuous: same logo, same teal field) ────────────
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(c.teal)
                        .statusBarsPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedBrandHeader()
                    /*Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.graphicsLayer {
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                        },
                    ) {
                        VBrandLogo(size = 44.dp, cornerRadius = 13.dp)
                        Text(
                            "EnRoll+",
                            style = VTheme.type.h2.colored(Color.White)
                                .copy(fontWeight = FontWeight.ExtraBold),
                        )
                    }*/
                }

                // 2 ── Hero headline + sub ──────────────────────────────────────────────────
                Column(Modifier.fillMaxWidth().padding(horizontal = d.lg, vertical = d.lg)) {
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { tabSlide(tab, prevTab) },
                        label = "landing-headline",
                    ) { t ->
                        Column {
                            Text(
                                if (t == 0) "Run your whole school\nfrom one screen."
                                else "Your child's school day,\nin your pocket.",
                                style = VTheme.type.h1.colored(c.ink),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                minLines = 3,
                                maxLines = 3,
                                text = if (t == 0)
                                    "Attendance, admissions, results, fees and parent messaging — one platform your staff actually want to use."
                                else
                                    "Attendance, marks, fees and messages from the school — clear, instant, and always up to date.",
                                style = VTheme.type.body.colored(c.ink2),
                            )
                        }
                    }
                }

                // 3 ── Tab switcher ─────────────────────────────────────────────────────────
                TabSwitcher(
                    selected = tab,
                    onSelect = {
                        if (it != tab) {
                            prevTab = tab
                            tab = it
                        }
                    },
                    modifier = Modifier.padding(horizontal = d.lg),
                )

                Spacer(Modifier.height(d.lg))

                // 4 ── Tab content: contained hero image + feature list ─────────────────────
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { tabSlide(tab, prevTab) },
                    label = "landing-content",
                ) { t ->
                    if (t == 0) {
                        TabPane(
                            heroImage = { ContainedHeroPhoto(it) },
                            features = SCHOOL_FEATURES,
                            imageA = Res.drawable.landing_school_1,
                            imageB = Res.drawable.landing_school_2,
                            networkImage = null,
                            imageLabel = "A real school, run on VidyaSetu",
                        )
                    } else {
                        TabPane(
                            heroImage = { ContainedHeroPhoto(it) },
                            features = PARENT_FEATURES,
                            imageA = Res.drawable.landing_school_2,
                            imageB = Res.drawable.landing_school_1,
                            networkImage = PARENT_HERO_URL,
                            imageLabel = "Stay close to your child's progress",
                        )
                    }
                }

                Spacer(Modifier.height(d.xl))
            }

            // 5 ── Sticky CTA dock ──────────────────────────────────────────────────────────
            CtaDock(
                tab = tab,
                onAdmin = onAdmin,
                onParent = onParent,
                onLegal = onLegal,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Feature inventory — each line verified against a real screen in this codebase.
// ─────────────────────────────────────────────────────────────────────────────

private data class LandingFeature(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

/** Schools tab — drawn from the Admin + Teacher screen set. */
private val SCHOOL_FEATURES = listOf(
    LandingFeature(
        VIcons.ListChecks,
        "Daily attendance in seconds",
        "Mark a whole class in one pass — absences alert the right parent the moment you save.",
    ),
    LandingFeature(
        VIcons.Users,
        "Admissions, end to end",
        "Track every enquiry from first call to enrolment, with follow-ups and conversion built in.",
    ),
    LandingFeature(
        VIcons.TrendingUp,
        "See trouble before it lands",
        "Class and faculty analytics surface the students and teachers who need attention early.",
    ),
    LandingFeature(
        VIcons.FileText,
        "Publish results, cleanly",
        "Enter marks, review the class spread, and release report-ready results in one flow.",
    ),
    LandingFeature(
        VIcons.Megaphone,
        "One voice to every parent",
        "Announcements, messages and PTM scheduling — every conversation in one place, forever.",
    ),
    LandingFeature(
        VIcons.ShieldCheck,
        "Accountability, not paperwork",
        "Syllabus coverage, leave approvals and teacher compliance, tracked without the spreadsheets.",
    ),
)

/** Parents tab — drawn from the Parent screen set. */
private val PARENT_FEATURES = listOf(
    LandingFeature(
        VIcons.Calendar,
        "Every day, accounted for",
        "A clear month calendar of present, late and absent days — no guessing, no chasing.",
    ),
    LandingFeature(
        VIcons.BookOpen,
        "Marks the moment they're in",
        "Real published results and syllabus progress for your child, the day the school releases them.",
    ),
    LandingFeature(
        VIcons.Wallet,
        "Fees without the friction",
        "See exactly what's due and what's paid, with the school's fee notices in the same place.",
    ),
    LandingFeature(
        VIcons.Chat,
        "Talk to the right teacher",
        "Message your child's class teacher or the school office directly — replies land in one thread.",
    ),
    LandingFeature(
        VIcons.Bell,
        "Never miss what matters",
        "School announcements and activity, filtered to what's relevant to your family.",
    ),
    LandingFeature(
        VIcons.Heart,
        "Apply for leave in a tap",
        "Request a day off and it routes straight to the class teacher, with status you can follow.",
    ),
)

/**
 * Parent hero — a permanent Unsplash CDN photograph (a parent and child reading together).
 * Permanent `images.unsplash.com` asset URLs are stable, unlike the deprecated `source.unsplash`
 * redirect endpoint. Loaded through coil with a [ShimmerBox] skeleton (same pattern as VAvatar).
 */
private const val PARENT_HERO_URL =
    "https://images.unsplash.com/photo-1503454537195-1dcabb73ffb9?auto=format&fit=crop&w=1200&q=70"

// ─────────────────────────────────────────────────────────────────────────────
// Tab switcher — clean pill indicator that springs between the two tabs.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TabSwitcher(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val labels = listOf("For Schools", "For Parents")

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(c.cream)
            .padding(4.dp),
    ) {
        // Layout: two equal-weight cells. The indicator pill is drawn behind the labels and
        // animated to the selected cell via animateDpAsState reading the measured half-width.
        var trackWidth by remember { mutableStateOf(0.dp) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val pillOffset by animateDpAsState(
            targetValue = if (selected == 0) 0.dp else trackWidth / 2f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "tab-pill",
        )
        val safeOffset = pillOffset.coerceAtLeast(0.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { with(density) { trackWidth = it.width.toDp() } },
        ) {
            // Indicator pill — the single use of the primary colour in this control.
            Box(
                Modifier
                    .padding(start = safeOffset)
                    .width(trackWidth / 2f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.tealDeep),
            )
            Row(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, label ->
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = VTheme.type.h4.colored(
                                if (selected == i) Color.White else c.ink2,
                            ).copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab content pane — contained hero photo + staggered feature list.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TabPane(
    heroImage: @Composable (HeroSpec) -> Unit,
    features: List<LandingFeature>,
    imageA: DrawableResource,
    imageB: DrawableResource,
    networkImage: String?,
    imageLabel: String,
) {
    val c = VTheme.colors
    val d = VTheme.dimens
    Column(Modifier.fillMaxWidth().padding(horizontal = d.lg)) {
        heroImage(HeroSpec(imageA, imageB, networkImage))
        Spacer(Modifier.height(10.dp))
        Text(imageLabel, style = VTheme.type.caption.colored(c.ink3))
        Spacer(Modifier.height(d.lg))
        features.forEachIndexed { i, f ->
            FeatureBlock(f, index = i)
            if (i != features.lastIndex) Spacer(Modifier.height(d.lg))
        }
    }
}

private data class HeroSpec(
    val bundledPrimary: DrawableResource,
    val bundledFallback: DrawableResource,
    val networkUrl: String?,
)

/**
 * ContainedHeroPhoto — one real photograph per tab, contained within the margins (not full-bleed),
 * 16:9, clipped to the card radius. Network images load through coil with a [ShimmerBox] skeleton
 * and fall back to a bundled professional photo; school images use the bundled photo directly.
 */
@Composable
private fun ContainedHeroPhoto(spec: HeroSpec) {
    val c = VTheme.colors
    val d = VTheme.dimens
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(d.radiusCard))
            .background(c.cream)
            .border(1.dp, c.hairline, RoundedCornerShape(d.radiusCard)),
    ) {
        // Bundled fallback always sits underneath so there is never a blank frame.
        Image(
            painter = painterResource(spec.bundledFallback),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (spec.networkUrl == null) {
            // School side: the bundled professional photo is the hero (no network dependency).
            Image(
                painter = painterResource(spec.bundledPrimary),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val painter = rememberAsyncImagePainter(model = spec.networkUrl)
            val state by painter.state.collectAsState()
            Crossfade(targetState = state, animationSpec = tween(300), label = "hero-load") { s ->
                when (s) {
                    is AsyncImagePainter.State.Success ->
                        Image(
                            painter = painter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    is AsyncImagePainter.State.Loading ->
                        ShimmerBox(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(d.radiusCard))
                    // Error/Empty → the bundled fallback beneath shows through.
                    else -> Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * FeatureBlock — the repeating three-line unit: monochrome 20dp icon, medium-weight name, and a
 * one-line mid-grey description. No card, no border, no background. The rhythm of repetition is the
 * design. Each block enters once with a small fade + slide, staggered by index (capped).
 */
@Composable
private fun FeatureBlock(feature: LandingFeature, index: Int) {
    val c = VTheme.colors
    val alpha = remember { Animatable(0f) }
    val ty = remember { Animatable(12f) }
    LaunchedEffect(Unit) {
        val delayMs = (index * 35L).coerceAtMost(150L)
        kotlinx.coroutines.delay(delayMs)
        launch { alpha.animateTo(1f, tween(260, easing = EaseOutCubic)) }
        launch { ty.animateTo(0f, tween(260, easing = EaseOutCubic)) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha.value
                translationY = ty.value * density
            },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            feature.icon,
            contentDescription = null,
            tint = c.ink,
            modifier = Modifier.size(20.dp).padding(top = 1.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                feature.title,
                style = VTheme.type.bodyStrong.colored(c.ink).copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                feature.description,
                style = VTheme.type.caption.colored(c.ink3).copy(lineHeight = 17.sp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sticky CTA dock — two buttons whose weight swaps with the active tab.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CtaDock(
    tab: Int,
    onAdmin: () -> Unit,
    onParent: () -> Unit,
    onLegal: (LegalDoc) -> Unit,
) {
    val c = VTheme.colors
    val d = VTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.background)
            .padding(top = 12.dp)
            .padding(horizontal = d.lg)
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        // Hairline above the dock to seat it visually above the scrolling content.
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        Spacer(Modifier.height(16.dp))

        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "cta-swap",
        ) { t ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (t == 0) {
                    VButton(
                        text = "Get Started — Schools",
                        onClick = onAdmin,
                        variant = VButtonVariant.Primary,
                        tone = VButtonTone.Teal,
                        soft = false,
                        size = VButtonSize.Lg,
                        full = true,
                        modifier = Modifier.weight(0.7f)
                    )

                    OutlinedCta(
                        text = "For Parents",
                        onClick = onParent,
                        modifier = Modifier.weight(0.3f)
                    )

                } else {
                    VButton(
                        text = "Get Started — Parents",
                        onClick = onParent,
                        variant = VButtonVariant.Primary,
                        tone = VButtonTone.Teal,
                        soft = false,
                        size = VButtonSize.Lg,
                        full = true,
                        modifier = Modifier.weight(0.7f)
                    )

                    OutlinedCta(
                        text = "For Schools",
                        onClick = onAdmin,
                        modifier = Modifier.weight(0.3f)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "By continuing you agree to our ",
                style = VTheme.type.caption.colored(c.ink3),
                textAlign = TextAlign.Center,
            )
            Text(
                "Terms",
                style = VTheme.type.caption.colored(c.tealDeep).copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onLegal(LegalDoc.Terms) },
            )
            Text(" & ", style = VTheme.type.caption.colored(c.ink3))
            Text(
                "Privacy",
                style = VTheme.type.caption.colored(c.tealDeep).copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onLegal(LegalDoc.Privacy) },
            )
        }
    }
}

/**
 * OutlinedCta — the secondary path. Primary-coloured border + text, no fill. Built directly rather
 * than via [VButton]'s Secondary variant (which carries a card fill + neutral ink) so the secondary
 * path reads as the same teal family as the primary, just unfilled — the two-choice contract.
 */
@Composable
private fun OutlinedCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                BorderStroke(1.5.dp, c.tealDeep),
                RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = VTheme.type.h4
                .colored(c.tealDeep)
                .copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Motion helpers.
// ─────────────────────────────────────────────────────────────────────────────

/** EaseOutCubic — the entry/feature easing the spec calls for (commonMain-safe Easing). */
private val EaseOutCubic = androidx.compose.animation.core.CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/**
 * tabSlide — horizontal slide whose direction follows the tab selection: moving to the right tab
 * slides content in from the right; moving to the left tab slides it in from the left. 280ms,
 * EaseOutCubic, cross-faded. Plays once per switch and settles.
 */
private fun tabSlide(target: Int, previous: Int): ContentTransform =
    if (target >= previous) {
        (slideInHorizontally(tween(280, easing = EaseOutCubic)) { it / 6 } + fadeIn(tween(280))) togetherWith
            (slideOutHorizontally(tween(280, easing = EaseOutCubic)) { -it / 6 } + fadeOut(tween(280)))
    } else {
        (slideInHorizontally(tween(280, easing = EaseOutCubic)) { -it / 6 } + fadeIn(tween(280))) togetherWith
            (slideOutHorizontally(tween(280, easing = EaseOutCubic)) { it / 6 } + fadeOut(tween(280)))
    }


@Composable
fun AnimatedBrandHeader(
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors

    var expandLogo by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }


    val logoScale by animateFloatAsState(
        targetValue = if (expandLogo) 1.5f else 1f,
        animationSpec = tween(
            durationMillis = 900,
            easing = FastOutSlowInEasing
        ),
        label = "logo-scale"
    )


    val logoTranslationX by animateDpAsState(
        targetValue = if (showText) (-5).dp else 0.dp,
        animationSpec = tween(
            durationMillis = 650,
            easing = FastOutSlowInEasing
        ),
        label = "logo-slide"
    )


    LaunchedEffect(Unit) {
        // Logo expansion
        expandLogo = true

        // Wait for logo settle
        delay(1100)

        // Reveal text + move logo
        showText = true
    }


    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(c.teal)
            .statusBarsPadding()
            .padding(
                top = 28.dp,
                bottom = 28.dp
            ),
        contentAlignment = Alignment.Center
    ) {


        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {


            // Fixed size container prevents clipping
            Box(
                modifier = Modifier
                    .size(75.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        translationX = logoTranslationX.toPx()
                    },
                contentAlignment = Alignment.Center
            ) {

                VBrandLogo(
                    size = 50.dp,
                    cornerRadius = 13.dp
                )
            }



            AnimatedVisibility(
                visible = showText,

                enter =
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = 700
                        )
                    )
                            +
                            slideInHorizontally(
                                initialOffsetX = {
                                    it / 2
                                },
                                animationSpec = tween(
                                    durationMillis = 700,
                                    easing = FastOutSlowInEasing
                                )
                            ),

                exit = fadeOut()
            ) {

                Text(
                    text = "EnRoll+",
                    style = VTheme.type.h1
                        .colored(Color.White)
                        .copy(
                            fontWeight = FontWeight.ExtraBold
                        )
                )
            }
        }
    }
}