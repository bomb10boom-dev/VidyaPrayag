package com.littlebridge.enrollplus.ui.v2.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VDivider
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VDatePicker
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.components.VProgressBar
import com.littlebridge.enrollplus.ui.v2.components.VTag
import com.littlebridge.enrollplus.ui.v2.theme.VPortalTone
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.feature.admin.presentation.AcademicInfoOBViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.BrandingInfoOBViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.InstitutionalBasicOBViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.LaunchInfoOBViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.OnboardingTeacherInput
import com.littlebridge.enrollplus.feature.admin.presentation.TeacherProvisioningOBViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * SchoolOnboardingScreenV2 — pixel-faithful Compose copy of `Auth.tsx → SchoolOnboarding`.
 *
 * The full dark (Night) 6-step setup wizard the Figma prototype renders, end-to-end interactive
 * from local state exactly like the React mock:
 *   1. **School identity**  — legal/short name, affiliation, board & type tags, principal.
 *   2. **Academic year**    — year tags, start/end dates, working days, period count.
 *   3. **Classes & sections** — per-class section chips + add-class controls.
 *   4. **Subjects**         — subject cards with per-class chips + "Apply to all".
 *   5. **Teachers**         — live coverage tracker + per-teacher subject×class matrix.
 *   6. **Students**         — CSV import card + validated preview.
 * A final "You're all set" completion screen with the stat bar and quick-start tiles closes it,
 * then [onComplete] opens the dashboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchoolOnboardingScreenV2(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    // Server step (BASIC|BRANDING|ACADEMIC|REVIEW) the wizard should resume on
    // for a returning/partially-onboarded admin. Mapped to the local 6-step
    // wizard index below. Defaults to BASIC (fresh start at step 1).
    resumeStep: String = "BASIC",
    basicVm: InstitutionalBasicOBViewModel = koinViewModel(),
    brandingVm: BrandingInfoOBViewModel = koinViewModel(),
    academicVm: AcademicInfoOBViewModel = koinViewModel(),
    launchVm: LaunchInfoOBViewModel = koinViewModel(),
    teacherProvisionVm: TeacherProvisioningOBViewModel = koinViewModel(),
) {
    VTheme(tone = VPortalTone.Warm) {
        val c = VTheme.colors
        val d = VTheme.dimens
        val titles = listOf("School identity", "Academic year", "Classes & sections", "Subjects", "Teachers", "Students")

        // Map the server's onboarding step to this wizard's 1-based step index.
        // BASIC→1, BRANDING→2 (academic-year UI), ACADEMIC→3 (classes), REVIEW→6.
        val initialStep = remember(resumeStep) {
            when (resumeStep.uppercase()) {
                "BRANDING" -> 2
                "ACADEMIC" -> 3
                "REVIEW" -> 6
                else -> 1
            }
        }
        var step by remember { mutableIntStateOf(initialStep) }

        // ── Step 1 — Identity inputs lifted to parent so we can submit BASIC ────
        var legalName by remember { mutableStateOf("") }
        var shortName by remember { mutableStateOf("") }
        var affiliation by remember { mutableStateOf("") }
        var board by remember { mutableStateOf("CBSE") }
        var schoolType by remember { mutableStateOf("Private Unaided") }
        var principalName by remember { mutableStateOf("") }
        var principalMobile by remember { mutableStateOf("") }

        // ── Classes built ──────────────────────────────────────────────────────────
        val classesBuilt = remember {
            mutableStateListOf(
                OBClass("Class 9", mutableStateListOf("A", "B")),
                OBClass("Class 10", mutableStateListOf("A", "B")),
            )
        }
        val classCodes: List<String> = classesBuilt.flatMap { cl -> cl.sections.map { "${cl.name.removePrefix("Class ")}-$it" } }

        // ── Subjects ───────────────────────────────────────────────────────────────
        val subjects = remember {
            mutableStateListOf(
                OBSubject("s1", "Mathematics", "MAT001", "Core", mutableStateListOf()),
                OBSubject("s2", "Science", "SCI001", "Core", mutableStateListOf()),
                OBSubject("s3", "English", "ENG001", "Core", mutableStateListOf()),
                OBSubject("s4", "Hindi", "HIN001", "Language", mutableStateListOf()),
                OBSubject("s5", "Social Studies", "SOC001", "Core", mutableStateListOf()),
                OBSubject("s6", "Computer Apps", "COMP01", "Core", mutableStateListOf()),
            )
        }

        // ── Teachers ───────────────────────────────────────────────────────────────
        // Start EMPTY — no fabricated staff. Admins add their teachers here (or skip
        // and provision them later from the dashboard). The matrix shows a zero-state
        // until a teacher is added, so we never persist invented teacher identities.
        val teachers = remember { mutableStateListOf<OBTeacher>() }
        var newTeacherName by remember { mutableStateOf("") }
        var newTeacherEmail by remember { mutableStateOf("") }

        // ── Backend state (per-step submit flags + errors) ─────────────────────────
        val basicSubmitting by basicVm.isSubmitting.collectAsStateV2()
        val basicError by basicVm.errorMessage.collectAsStateV2()
        val brandingSubmitting by brandingVm.isSubmitting.collectAsStateV2()
        val brandingError by brandingVm.errorMessage.collectAsStateV2()
        val academicSubmitting by academicVm.isSubmitting.collectAsStateV2()
        val academicError by academicVm.errorMessage.collectAsStateV2()
        val launchSubmitting by launchVm.isSubmitting.collectAsStateV2()
        val launchError by launchVm.errorMessage.collectAsStateV2()

        // Which step's network call (if any) is currently running. Drives the
        // Continue button's spinner + disables Back while a submit is in-flight.
        val isSubmitting = basicSubmitting || brandingSubmitting || academicSubmitting || launchSubmitting

        // Surface the first non-null backend error, if any. Step screens display
        // it inline below their content.
        val currentError: String? = when (step) {
            1 -> basicError
            2 -> brandingError
            3, 4, 5 -> academicError
            else -> launchError
        }

        // Truthful Continue handler — submit the correct OB step to the backend
        // and only advance the wizard once the server accepts it.
        fun continueClicked() {
            when (step) {
                1 -> {
                    // Push the lifted identity inputs into the BASIC VM and submit.
                    // NOTE: Step 1 collects no email — we do NOT synthesise a fake
                    // one (that would overwrite the real contact email captured at
                    // registration). Only the values the admin actually typed go up.
                    basicVm.updateSchoolName(legalName)
                    basicVm.updateBoard(board)
                    basicVm.updateContact(principalMobile.replace(Regex("[^0-9]"), "").take(10))
                    basicVm.submit(onSuccess = { step++ })
                }
                2 -> {
                    // Academic-year step has no logo/brand color UI — submit BRANDING
                    // with the VM's defaults so the server advances its state machine.
                    brandingVm.submit(onSuccess = { step++ })
                }
                3, 4 -> {
                    // Classes & Subjects are still being edited — purely local advance.
                    // ACADEMIC payload is built and submitted on step 5 → 6 transition.
                    step++
                }
                5 -> {
                    // Teachers step done. TWO real persistence actions happen here:
                    //
                    // 1) Provision a REAL `role=teacher` account for every teacher the
                    //    admin entered with an email (POST /school/teachers). This is
                    //    what makes the teacher able to LOG IN — the assignment matrix
                    //    alone never created an account. Best-effort + non-blocking:
                    //    a duplicate/failure for one teacher won't stop onboarding.
                    //
                    // 2) Persist the REAL class/section/subject structure (+ teacher
                    //    name on each assigned subject) via the ACADEMIC step.
                    val builtClasses: List<Pair<String, List<String>>> =
                        classesBuilt.map { it.name to it.sections.toList() }
                    val builtSubjects: List<Pair<String, String>> =
                        subjects.map { it.name to it.code }
                    // "subjectName|classCode" -> teacher name, from the assignment matrix.
                    val teacherMap: Map<String, String> = buildMap {
                        teachers.forEach { t ->
                            t.assignments.forEach { (subjName, classCode) ->
                                put("$subjName|$classCode", t.name)
                            }
                        }
                    }
                    // Real teacher accounts to create (only those given an email/phone).
                    val toProvision = teachers
                        .filter { it.identifier.isNotBlank() }
                        .map { OnboardingTeacherInput(name = it.name, identifier = it.identifier) }

                    teacherProvisionVm.provisionAll(toProvision) {
                        // Whether or not staff were provisioned, persist the academic
                        // structure and advance only when the server accepts it.
                        academicVm.applyBuiltStructure(builtClasses, builtSubjects, teacherMap)
                        academicVm.submit(onSuccess = { step++ })
                    }
                }
                6 -> {
                    // Final REVIEW submit with is_final_submission=true.
                    launchVm.submit(onSuccess = { step++ })
                }
            }
        }

        // ── Completion screen ────────────────────────────────────────────────────
        if (step > 6) {
            // Real school name from the server's REVIEW step (no hardcoding). Fall
            // back to the name the admin typed in Step 1 if the server hasn't echoed
            // it yet, then to a neutral label — never a sample school.
            val launchState by launchVm.state.collectAsStateV2()
            val provisionState by teacherProvisionVm.state.collectAsStateV2()
            val resolvedName = launchState.schoolName
                .takeIf { it.isNotBlank() && it != "—" }
                ?: legalName.takeIf { it.isNotBlank() }
                ?: "Your school"
            CompletionScreen(
                schoolName = resolvedName,
                provisionedTeachers = provisionState.results,
                onComplete = onComplete,
            )
            return@VTheme
        }

        // §11 cross-platform — wrap the screen in safe-area padding before any
        // content. statusBars first (top hero), imePadding lifts the form when
        // the keyboard opens. The footer Row applies navigationBarsPadding
        // separately so the CTA never sits under the gesture bar even when
        // the AnimatedContent body is empty.
        Column(
            modifier
                .fillMaxSize()
                .background(c.background)
                .statusBarsPadding()
                .imePadding(),
        ) {
            // ── Header + step indicator ─────────────────────────────────────────────
            Column(Modifier.fillMaxWidth().padding(horizontal = d.lg, vertical = d.md)) {
                Spacer(Modifier.height(d.sm))
                Text("ONBOARDING", style = VTheme.type.labelStrong.colored(c.ink3))
                Spacer(Modifier.height(d.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.xs)) {
                    repeat(6) { i ->
                        // Smooth fill — each completed bar cross-fades into teal vs the inactive grey.
                        val active = i < step
                        val targetColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (active) c.teal else c.ink.copy(alpha = 0.08f),
                            animationSpec = tween(durationMillis = 250),
                            label = "stepBar$i",
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(targetColor),
                        )
                    }
                }
                Spacer(Modifier.height(d.md))
                Text(titles[step - 1], style = VTheme.type.h2.colored(c.ink))
                Text("Step $step of 6", style = VTheme.type.caption.colored(c.ink3))
            }

            // §13.2 — wrap the per-step body in an AnimatedContent so each step
            // slides in from the side of the press direction (Continue→right→left;
            // Back→left→right). Fades cross to keep the transition gentle. The
            // outer Column owns weight(1f) + scroll so step swaps don't jump.
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    val dur = 280
                    val enter = slideInHorizontally(
                        animationSpec = tween(dur),
                        initialOffsetX = { if (forward) it / 4 else -it / 4 },
                    ) + fadeIn(tween(dur))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(dur),
                        targetOffsetX = { if (forward) -it / 4 else it / 4 },
                    ) + fadeOut(tween(dur))
                    enter togetherWith exit
                },
                label = "onboardingStep",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { current ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = d.lg),
                    verticalArrangement = Arrangement.spacedBy(d.md),
                ) {
                    when (current) {
                        1 -> IdentityStep(
                            legalName = legalName, onLegalNameChange = { legalName = it },
                            shortName = shortName, onShortNameChange = { shortName = it },
                            affiliation = affiliation, onAffiliationChange = { affiliation = it },
                            board = board, onBoardChange = { board = it },
                            schoolType = schoolType, onSchoolTypeChange = { schoolType = it },
                            principal = principalName, onPrincipalChange = { principalName = it },
                            principalMobile = principalMobile, onPrincipalMobileChange = { principalMobile = it },
                        )
                        2 -> AcademicYearStep()
                        3 -> ClassesStep(classesBuilt)
                        4 -> SubjectsStep(subjects, classCodes)
                        5 -> TeachersStep(
                            teachers, subjects, classCodes,
                            newTeacherName = newTeacherName,
                            onNewTeacherNameChange = { newTeacherName = it },
                            newTeacherEmail = newTeacherEmail,
                            onNewTeacherEmailChange = { newTeacherEmail = it },
                            onAddTeacher = {
                                val nm = newTeacherName.trim()
                                val em = newTeacherEmail.trim()
                                if (nm.isNotBlank()) {
                                    teachers.add(
                                        OBTeacher(
                                            id = "t${teachers.size + 1}",
                                            name = nm,
                                            identifier = em,        // email → real login account on submit
                                            mobile = "",
                                            username = em,          // show the email as the meta line
                                            assignments = mutableStateListOf(),
                                        )
                                    )
                                    newTeacherName = ""
                                    newTeacherEmail = ""
                                }
                            },
                        )
                        else -> StudentsStep()
                    }
                    // Inline backend error for the current step (LAW 3 — Error leg).
                    val errMsg = currentError
                    if (!errMsg.isNullOrBlank()) {
                        Spacer(Modifier.height(d.xs))
                        Text(
                            errMsg,
                            style = VTheme.type.caption.colored(c.dangerInk),
                        )
                    }
                    Spacer(Modifier.height(d.sm))
                }
            }

            // ── Footer nav (top hairline border, like React borderTop --border-dark-1) ──
            VDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    // §11.3 — keep the bottom CTAs above the gesture bar on Android +
                    // home indicator on iOS.
                    .navigationBarsPadding()
                    .padding(horizontal = d.lg, vertical = d.md),
                horizontalArrangement = Arrangement.spacedBy(d.sm),
            ) {
                if (step > 1) {
                    VButton(
                        text = "Back",
                        onClick = { if (!isSubmitting) step-- },
                        variant = VButtonVariant.Ghost,
                        tone = VButtonTone.Navy,
                        enabled = !isSubmitting,
                    )
                }
                VButton(
                    text = if (step < 6) "Continue" else "Finish setup",
                    onClick = { continueClicked() },
                    full = true,
                    size = VButtonSize.Lg,
                    tone = if (step == 6) VButtonTone.Teal else VButtonTone.Navy,
                    loading = isSubmitting,
                    enabled = !isSubmitting,
                    successLabel = "Setting up",
                    trailing = { Icon(VIcons.ArrowRight, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}

// ═══ Step 1 — School identity ═══════════════════════════════════════════════════
// State is HOISTED to the parent so that the Continue handler can read the
// final values and push them into [InstitutionalBasicOBViewModel] before
// submitting BASIC to the backend.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityStep(
    legalName: String, onLegalNameChange: (String) -> Unit,
    shortName: String, onShortNameChange: (String) -> Unit,
    affiliation: String, onAffiliationChange: (String) -> Unit,
    board: String, onBoardChange: (String) -> Unit,
    schoolType: String, onSchoolTypeChange: (String) -> Unit,
    principal: String, onPrincipalChange: (String) -> Unit,
    principalMobile: String, onPrincipalMobileChange: (String) -> Unit,
) {
    val c = VTheme.colors
    val d = VTheme.dimens

    VInput(legalName, onLegalNameChange, label = "Full legal name", placeholder = "Saraswati Vidya Mandir", modifier = Modifier.fillMaxWidth())
    VInput(shortName, onShortNameChange, label = "Short name", placeholder = "SVM", modifier = Modifier.fillMaxWidth())
    VInput(affiliation, onAffiliationChange, label = "Affiliation number", placeholder = "UP/CBSE/2021/4421", modifier = Modifier.fillMaxWidth())

    Text("BOARD", style = VTheme.type.labelStrong.colored(c.ink3))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(d.sm), verticalArrangement = Arrangement.spacedBy(d.sm)) {
        listOf("CBSE", "ICSE", "UP State", "Other").forEach { b ->
            VTag(text = b, active = board == b, onClick = { onBoardChange(b) })
        }
    }
    Text("SCHOOL TYPE", style = VTheme.type.labelStrong.colored(c.ink3))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(d.sm), verticalArrangement = Arrangement.spacedBy(d.sm)) {
        listOf("Government", "Private Aided", "Private Unaided", "Central").forEach { t ->
            VTag(text = t, active = schoolType == t, onClick = { onSchoolTypeChange(t) })
        }
    }
    VInput(principal, onPrincipalChange, label = "Principal's name", placeholder = "Dr. Anita Verma", modifier = Modifier.fillMaxWidth())
    VInput(principalMobile, onPrincipalMobileChange, label = "Principal's mobile", placeholder = "+91 98XXX XXXXX", modifier = Modifier.fillMaxWidth())
}

// ═══ Step 2 — Academic year ═════════════════════════════════════════════════════
@Composable
private fun AcademicYearStep() {
    val c = VTheme.colors
    val d = VTheme.dimens
    var year by remember { mutableStateOf("2025-26") }
    var workingDays by remember { mutableStateOf("Mon–Sat") }
    var starts by remember { mutableStateOf("") }
    var ends by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var periods by remember { mutableStateOf("") }

    Text("CURRENT ACADEMIC YEAR", style = VTheme.type.labelStrong.colored(c.ink3))
    Row(horizontalArrangement = Arrangement.spacedBy(d.sm)) {
        listOf("2025-26", "2026-27").forEach { y -> VTag(text = y, active = year == y, onClick = { year = y }) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(d.sm)) {
        VDatePicker(starts, { starts = it }, label = "Year starts", placeholder = "Start date", modifier = Modifier.weight(1f))
        VDatePicker(ends, { ends = it }, label = "Year ends", placeholder = "End date", modifier = Modifier.weight(1f))
    }
    Text("WORKING DAYS", style = VTheme.type.labelStrong.colored(c.ink3))
    Row(horizontalArrangement = Arrangement.spacedBy(d.sm)) {
        VTag(text = "Mon–Fri", active = workingDays == "Mon–Fri", onClick = { workingDays = "Mon–Fri" })
        VTag(text = "Mon–Sat", active = workingDays == "Mon–Sat", onClick = { workingDays = "Mon–Sat" })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(d.sm)) {
        VInput(startTime, { startTime = it }, label = "Start time", placeholder = "08:00 AM", modifier = Modifier.weight(1f))
        VInput(endTime, { endTime = it }, label = "End time", placeholder = "02:00 PM", modifier = Modifier.weight(1f))
    }
    VInput(periods, { periods = it }, label = "Periods per day", placeholder = "8", modifier = Modifier.fillMaxWidth())
}

// ═══ Step 3 — Classes & sections ════════════════════════════════════════════════
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassesStep(classesBuilt: MutableList<OBClass>) {
    val c = VTheme.colors
    val d = VTheme.dimens
    var newClassName by remember { mutableStateOf("") }

    VCard(modifier = Modifier.fillMaxWidth()) {
        Text("TIP", style = VTheme.type.labelStrong.colored(c.ink3))
        Text(
            "Pick the sections your school actually runs. Subjects and teachers in the next steps will only show these classes.",
            style = VTheme.type.caption.colored(c.ink2),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    classesBuilt.forEachIndexed { idx, cl ->
        VCard(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(cl.name, style = VTheme.type.bodyStrong.colored(c.ink), modifier = Modifier.weight(1f))
                Text("${cl.sections.size} sections", style = VTheme.type.dataSm.colored(c.ink3))
            }
            Spacer(Modifier.height(d.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(d.xs), verticalArrangement = Arrangement.spacedBy(d.xs)) {
                listOf("A", "B", "C", "D", "E", "F").forEach { s ->
                    val on = cl.sections.contains(s)
                    VTag(text = s, active = on, onClick = {
                        if (on) cl.sections.remove(s) else { cl.sections.add(s); cl.sections.sort() }
                    })
                }
            }
        }
    }
    VCard(modifier = Modifier.fillMaxWidth()) {
        Text("ADD CLASS MANUALLY", style = VTheme.type.labelStrong.colored(c.ink3))
        Spacer(Modifier.height(d.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(d.sm), verticalAlignment = Alignment.CenterVertically) {
            VInput(newClassName, { newClassName = it }, placeholder = "e.g. Class 11, Nursery, KG", modifier = Modifier.weight(1f))
            VButton(
                text = "Add",
                onClick = {
                    if (newClassName.isNotBlank()) {
                        classesBuilt.add(OBClass(newClassName.trim(), mutableStateListOf("A")))
                        newClassName = ""
                    }
                },
                tone = VButtonTone.Teal,
                size = VButtonSize.Sm,
                enabled = newClassName.isNotBlank(),
            )
        }
        Spacer(Modifier.height(d.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(d.xs), verticalArrangement = Arrangement.spacedBy(d.xs)) {
            listOf("Class 11", "Class 12", "Nursery", "LKG", "UKG").forEach { q ->
                VTag(text = "+ $q", onClick = { classesBuilt.add(OBClass(q, mutableStateListOf("A"))) })
            }
        }
    }
}

// ═══ Step 4 — Subjects ══════════════════════════════════════════════════════════
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectsStep(subjects: MutableList<OBSubject>, classCodes: List<String>) {
    val c = VTheme.colors
    val d = VTheme.dimens

    VCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("SUBJECTS OFFERED", style = VTheme.type.labelStrong.colored(c.ink3))
                Text("Tap a subject's class chips to set where it's taught.", style = VTheme.type.caption.colored(c.ink3), modifier = Modifier.padding(top = 2.dp))
            }
            Text(
                "Apply to all",
                style = VTheme.type.caption.colored(c.tealDeep),
                modifier = Modifier.padding(start = d.sm),
            )
        }
    }
    subjects.forEach { s ->
        VCard(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = VTheme.type.bodyStrong.colored(c.ink))
                    Text("${s.code} · ${s.type}", style = VTheme.type.dataSm.colored(c.ink3))
                }
                VBadge(
                    text = if (s.classes.isEmpty()) "No classes" else "${s.classes.size} / ${classCodes.size}",
                    tone = if (s.classes.isEmpty()) VBadgeTone.Warning else VBadgeTone.Success,
                )
            }
            Spacer(Modifier.height(d.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(d.xs), verticalArrangement = Arrangement.spacedBy(d.xs)) {
                classCodes.forEach { cc ->
                    val on = s.classes.contains(cc)
                    VTag(text = cc, active = on, onClick = { if (on) s.classes.remove(cc) else s.classes.add(cc) })
                }
            }
        }
    }
}

// ═══ Step 5 — Teachers (coverage + matrix) ══════════════════════════════════════
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeachersStep(
    teachers: MutableList<OBTeacher>,
    subjects: List<OBSubject>,
    classCodes: List<String>,
    newTeacherName: String,
    onNewTeacherNameChange: (String) -> Unit,
    newTeacherEmail: String,
    onNewTeacherEmailChange: (String) -> Unit,
    onAddTeacher: () -> Unit,
) {
    val c = VTheme.colors
    val d = VTheme.dimens

    // ── Add teacher (real input only — nothing pre-filled) ──────────────────
    // Adding a teacher WITH a work email creates a real login account on the
    // next step: the teacher gets a generated initial password and signs in via
    // School Administration → email + password (forced to reset on first login).
    // Name-only entries are still usable for subject assignment but create no
    // login — those teachers can be provisioned later from the dashboard.
    VCard(modifier = Modifier.fillMaxWidth()) {
        Text("ADD A TEACHER", style = VTheme.type.labelStrong.colored(c.ink3))
        Text(
            "Enter a work email to create the teacher's login account now — they'll " +
                "get a one-time password to sign in. Name only? You can add their " +
                "login later from the dashboard.",
            style = VTheme.type.caption.colored(c.ink3),
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(d.sm))
        VInput(
            newTeacherName,
            onNewTeacherNameChange,
            label = "Full name",
            placeholder = "Mrs. Kavita Nair",
            leadingIcon = VIcons.User,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(d.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(d.sm), verticalAlignment = Alignment.Bottom) {
            VInput(
                newTeacherEmail,
                onNewTeacherEmailChange,
                label = "Work email (optional)",
                placeholder = "kavita@svm.edu.in",
                leadingIcon = VIcons.Mail,
                keyboardType = KeyboardType.Email,
                modifier = Modifier.weight(1f),
            )
            VButton(
                text = "Add",
                onClick = onAddTeacher,
                tone = VButtonTone.Teal,
                size = VButtonSize.Lg,
                enabled = newTeacherName.isNotBlank(),
            )
        }
    }

    if (teachers.isEmpty()) {
        VCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = d.md), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(VIcons.User, contentDescription = null, tint = c.ink3, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(d.xs))
                Text("No teachers added yet", style = VTheme.type.bodyStrong.colored(c.ink))
                Text("Add a teacher above to assign subjects, or continue and do it later.", style = VTheme.type.caption.colored(c.ink3), textAlign = TextAlign.Center)
            }
        }
        return
    }

    val allSlots = subjects.flatMap { s -> s.classes.map { s.name to it } }
    val assignedSlots = teachers.flatMap { it.assignments }
    val coveredCount = allSlots.count { slot -> assignedSlots.any { it.first == slot.first && it.second == slot.second } }
    val coverage = if (allSlots.isNotEmpty()) (coveredCount * 100 / allSlots.size) else 0

    // Coverage tracker
    VCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("TEACHER COVERAGE", style = VTheme.type.labelStrong.colored(c.ink3))
                Text("$coveredCount of ${allSlots.size} subject × class slots assigned", style = VTheme.type.caption.colored(c.ink3), modifier = Modifier.padding(top = 2.dp))
            }
            Text(
                "$coverage%",
                style = VTheme.type.dataLg.colored(if (coverage == 100) c.successInk else if (coverage > 50) c.tealDeep else c.warningInk),
            )
        }
        Spacer(Modifier.height(d.sm))
        VProgressBar(value = coverage.toFloat(), modifier = Modifier.fillMaxWidth())
        if (coverage < 100 && allSlots.isNotEmpty()) {
            Spacer(Modifier.height(d.xs))
            Text("${allSlots.size - coveredCount} unassigned — keep adding assignments below.", style = VTheme.type.caption.colored(c.warningInk))
        }
    }

    teachers.forEach { t ->
        VCard(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                VAvatar(name = t.name, size = 40.dp)
                Spacer(Modifier.width(d.md))
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = VTheme.type.bodyStrong.colored(c.ink))
                    val meta = listOf(t.username, t.mobile).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = VTheme.type.dataSm.colored(c.ink3))
                    }
                }
                VBadge(text = "${t.assignments.size} slots", tone = if (t.assignments.isNotEmpty()) VBadgeTone.Arctic else VBadgeTone.Neutral)
            }
            Spacer(Modifier.height(d.sm))
            // ── Assignment matrix — bordered header grid (110px + repeat(N,1fr)) ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, c.shadowTint.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
            ) {
                // header row — cream bg, uppercase labels
                Row(Modifier.fillMaxWidth().background(c.cream), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SUBJECT",
                        style = VTheme.type.labelStrong.colored(c.ink3).copy(fontSize = 10.sp, letterSpacing = 0.05.em),
                        modifier = Modifier.width(110.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    classCodes.forEach { cc ->
                        Text(
                            cc,
                            style = VTheme.type.dataSm.colored(c.ink3).copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                }
                // subject rows
                subjects.filter { it.classes.isNotEmpty() }.forEachIndexed { rowI, s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (rowI > 0) Modifier.border(0.dp, Color.Transparent) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.name, style = VTheme.type.caption.colored(c.ink).copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.width(110.dp).padding(horizontal = 10.dp, vertical = 8.dp))
                        classCodes.forEach { cc ->
                            val inSubject = s.classes.contains(cc)
                            val mine = t.assignments.any { it.first == s.name && it.second == cc }
                            val takenByOther = !mine && teachers.any { other -> other.id != t.id && other.assignments.any { it.first == s.name && it.second == cc } }
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                MatrixCell(
                                    label = if (!inSubject) "" else if (mine) "✓" else if (takenByOther) "—" else "+",
                                    inSubject = inSubject,
                                    mine = mine,
                                    takenByOther = takenByOther,
                                    enabled = inSubject && !takenByOther,
                                    onClick = {
                                        if (mine) t.assignments.removeAll { it.first == s.name && it.second == cc }
                                        else if (inSubject && !takenByOther) t.assignments.add(s.name to cc)
                                    },
                                )
                            }
                        }
                    }
                    if (rowI < subjects.count { it.classes.isNotEmpty() } - 1) {
                        VDivider(color = c.shadowTint.copy(alpha = 0.05f))
                    }
                }
            }
            // assignment chip summary row (font-mono, teal pill SUBJ·class)
            if (t.assignments.isNotEmpty()) {
                Spacer(Modifier.height(d.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(d.xs), verticalArrangement = Arrangement.spacedBy(d.xs)) {
                    t.assignments.forEach { a ->
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(c.teal.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "${a.first.take(4)}·${a.second}",
                                style = VTheme.type.dataSm.colored(c.tealDeep).copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                            )
                        }
                    }
                }
            }
        }
    }

    VButton(
        text = "Import roster from CSV",
        onClick = {},
        full = true,
        tone = VButtonTone.Sand,
        leading = { Icon(VIcons.Upload, contentDescription = null, modifier = Modifier.size(14.dp)) },
    )
}

@Composable
private fun MatrixCell(label: String, inSubject: Boolean, mine: Boolean, takenByOther: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = VTheme.colors
    val available = inSubject && !mine && !takenByOther
    val bg = when {
        !inSubject -> Color.Transparent
        mine -> c.tealDeep
        takenByOther -> c.cream
        else -> c.teal.copy(alpha = 0.10f)
    }
    val fg = when {
        mine -> Color.White
        takenByOther -> c.ink3
        else -> c.tealDeep
    }
    var cell = Modifier
        .padding(4.dp)
        .height(32.dp)
        .fillMaxWidth()
        .clip(RoundedCornerShape(6.dp))
        .background(bg)
    if (available) {
        // 1px dashed available border rgba(0,106,96,0.35)
        cell = cell.dashedBorder(c.tealDeep.copy(alpha = 0.35f), strokeWidth = 1.dp, cornerRadius = 6.dp)
    }
    if (enabled) cell = cell.clickableNoRipple(onClick)
    Box(
        cell.then(if (!inSubject) Modifier.alpha(0.25f) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            Text(label, style = VTheme.type.caption.colored(fg).copy(fontWeight = FontWeight.Bold, fontSize = 11.sp))
        }
    }
}

// ═══ Step 6 — Students ══════════════════════════════════════════════════════════
@Composable
private fun StudentsStep() {
    val c = VTheme.colors
    val d = VTheme.dimens

    VCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = d.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(VIcons.Upload, contentDescription = null, tint = c.ink3, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(d.sm))
            Text("Drop your students CSV here", style = VTheme.type.bodyStrong.colored(c.ink))
            Text("or tap to browse", style = VTheme.type.caption.colored(c.ink3))
            Spacer(Modifier.height(d.md))
            VButton(text = "Download template", onClick = {}, variant = VButtonVariant.Secondary, size = VButtonSize.Sm)
        }
    }
    VCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("No roster imported yet", style = VTheme.type.bodyStrong.colored(c.ink), modifier = Modifier.weight(1f))
            VBadge(text = "Optional", tone = VBadgeTone.Neutral)
        }
        Spacer(Modifier.height(d.sm))
        Text(
            "Importing students now is optional — you can finish setup and add students " +
                "anytime from your dashboard. Validation results will appear here once a CSV is uploaded.",
            style = VTheme.type.caption.colored(c.ink3),
        )
    }
}

// ═══ Completion screen ══════════════════════════════════════════════════════════
@Composable
private fun CompletionScreen(
    schoolName: String,
    provisionedTeachers: List<com.littlebridge.enrollplus.feature.admin.presentation.ProvisionedTeacher> = emptyList(),
    onComplete: () -> Unit,
) {
    val c = VTheme.colors
    val d = VTheme.dimens

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Teal hero — vertical gradient #3cb9a9 → #2a8f80 + radial white glow
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(c.teal, Color(0xFF2A8F80))),
                )
                .drawBehind {
                    // radial glow circle at 50% 30%, white@30%, fade to transparent ~55%
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.30f),
                            radius = size.maxDimension * 0.55f,
                        ),
                        radius = size.maxDimension * 0.55f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.30f),
                    )
                },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 64.dp, bottom = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(26.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "You're all set",
                    style = VTheme.type.h1.colored(Color.White).copy(fontSize = 30.sp, letterSpacing = (-0.02).em),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text("$schoolName is live on VidyaPrayag.", style = VTheme.type.body.colored(Color.White.copy(alpha = 0.88f)), textAlign = TextAlign.Center)
            }
        }

        // Body
        Column(Modifier.fillMaxWidth().padding(d.lg), verticalArrangement = Arrangement.spacedBy(d.md)) {
            OnboardingCompletedCard()
            val created = provisionedTeachers.filter { it.created && !it.initialPassword.isNullOrBlank() }
            if (created.isNotEmpty()) {
                Text("TEACHER LOGINS CREATED", style = VTheme.type.labelStrong.colored(c.ink3))
                VCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Share these one-time passwords with your teachers. They'll be " +
                            "asked to set their own password on first sign-in. You won't " +
                            "see these again — reset anytime from the dashboard.",
                        style = VTheme.type.caption.colored(c.ink3),
                    )
                    created.forEach { t ->
                        Spacer(Modifier.height(d.sm))
                        VDivider()
                        Spacer(Modifier.height(d.sm))
                        Text(t.name, style = VTheme.type.bodyStrong.colored(c.ink))
                        Text(t.identifier, style = VTheme.type.dataSm.colored(c.ink3))
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Password: ", style = VTheme.type.caption.colored(c.ink3))
                            Text(
                                t.initialPassword.orEmpty(),
                                style = VTheme.type.dataSm.colored(c.tealDeep).copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }
            // Surface any teacher accounts that could NOT be created (e.g. the
            // email already exists) so the admin isn't misled into thinking they
            // were provisioned. Honest, not silent.
            val failed = provisionedTeachers.filter { !it.created }
            if (failed.isNotEmpty()) {
                VCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Couldn't create some logins", style = VTheme.type.bodyStrong.colored(c.warningInk))
                    failed.forEach { t ->
                        Text(
                            "${t.name} (${t.identifier}) — ${t.message ?: "failed"}. Add them later from the dashboard.",
                            style = VTheme.type.caption.colored(c.ink3),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(d.xs))
            VButton(
                text = "Open dashboard",
                onClick = onComplete,
                full = true,
                size = VButtonSize.Lg,
                tone = VButtonTone.Teal,
                trailing = { Icon(VIcons.ArrowRight, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            Text(
                "You can edit any of this later in Settings.",
                style = VTheme.type.caption.colored(c.ink3),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Local wizard models (mirror Auth.tsx OB* types) ─────────────────────────────
private class OBClass(val name: String, val sections: androidx.compose.runtime.snapshots.SnapshotStateList<String>)
private class OBSubject(
    val id: String,
    val name: String,
    val code: String,
    val type: String,
    val classes: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
)
private class OBTeacher(
    val id: String,
    val name: String,
    // Email/phone the teacher will sign in with. When set (email), the wizard
    // provisions a REAL `role=teacher` account on submit so the teacher can log
    // in. Blank = name-only (cosmetic assignment matrix; no login created).
    val identifier: String,
    val mobile: String,
    val username: String,
    val assignments: androidx.compose.runtime.snapshots.SnapshotStateList<Pair<String, String>>,
)

/** Tiny clickable helper. */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

/** 1px dashed rounded border (the React `border: 1px dashed` available-cell style). */
private fun Modifier.dashedBorder(color: Color, strokeWidth: androidx.compose.ui.unit.Dp, cornerRadius: androidx.compose.ui.unit.Dp): Modifier =
    this.drawBehind {
        val sw = strokeWidth.toPx()
        val r = cornerRadius.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(sw * 3f, sw * 3f), 0f)
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(sw / 2f, sw / 2f),
            size = Size(size.width - sw, size.height - sw),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = sw, pathEffect = dash),
        )
    }

@Preview
@Composable
fun OnboardingCompletedCard(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit = {},
) {
    val c = VTheme.colors

    var animateSuccess by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (animateSuccess) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "success-scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (animateSuccess) 1f else 0f,
        animationSpec = tween(500),
        label = "success-alpha"
    )

    LaunchedEffect(Unit) {
        animateSuccess = true
    }


    VCard(
        modifier = modifier
            .fillMaxWidth()
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {


            // Animated success badge
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        var scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(CircleShape)
                    .background(Color(0xFFDCF2EF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = VIcons.Check,
                    contentDescription = null,
                    tint = Color(0xFF006A60),
                    modifier = Modifier.size(36.dp)
                )
            }


            Spacer(Modifier.height(16.dp))


            Text(
                text = "Your school is ready 🎉",
                style = VTheme.type.h3
                    .colored(c.ink)
                    .copy(
                        fontWeight = FontWeight.Bold
                    ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your profile setup is complete. You can now start building your digital campus by adding teachers, students and parents.",
                style = VTheme.type.body
                    .colored(c.ink3),
                textAlign = TextAlign.Center
            )
        }
    }
}