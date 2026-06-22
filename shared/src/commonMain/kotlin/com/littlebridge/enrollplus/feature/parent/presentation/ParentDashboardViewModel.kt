package com.littlebridge.enrollplus.feature.parent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.core.state.SelectedChildHolder
import com.littlebridge.enrollplus.feature.parent.domain.model.DashboardAlertDto
import com.littlebridge.enrollplus.feature.parent.domain.model.DashboardChildSummary
import com.littlebridge.enrollplus.feature.parent.domain.model.FeeData
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentAttendanceData
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentHolidayDto
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentMarkDto
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentPeriodDto
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentSyllabusData
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentSyllabusUnitDto
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentTimetableData
import com.littlebridge.enrollplus.feature.parent.domain.repository.ParentRepository
import com.littlebridge.enrollplus.util.dayOfWeek
import com.littlebridge.enrollplus.util.nowMinutesOfDay
import com.littlebridge.enrollplus.util.parseHourMinute
import com.littlebridge.enrollplus.util.parseIsoDate
import com.littlebridge.enrollplus.util.todayIso
import com.littlebridge.enrollplus.util.todayWeekday
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ParentDashboardViewModel — the single source of truth behind the rebuilt parent
 * dashboard (the reference `PhoneMockup.tsx` redesign).
 *
 * It aggregates EVERY real backend read the new dashboard surfaces, all scoped to the
 * shared selected child (RA-S05 [SelectedChildHolder]) so the child switcher reactively
 * re-drives the whole screen:
 *   - dashboard  → children list, greeting, alerts            (GET /parent/dashboard)
 *   - attendance → today's status + month records + holidays  (GET /parent/child/{id}/attendance)
 *   - timetable  → the child's class weekly schedule           (GET /parent/child/{id}/timetable)
 *   - syllabus   → "covered today" + coverage breakdown        (GET /parent/child/{id}/syllabus)
 *   - marks      → latest published result for the academics card (GET /parent/child/{id}/marks)
 *
 * NOTHING here is fabricated — every number/state is derived from these endpoints. The
 * "today" computations (attendance state, current period, covered-today, end-of-day) are
 * derived from the device's local clock against the real records, so the dashboard reads
 * live as the school day progresses.
 */

/** Distinct attendance states the dashboard must render — LAW: every state handled. */
enum class AttendanceDayState { Present, Absent, Late, Holiday, Sunday, Vacation, NoData }

/** A resolved "today" attendance verdict for the selected child. */
data class TodayAttendance(
    val state: AttendanceDayState,
    /** Holiday/vacation title when state is Holiday/Vacation; else "". */
    val label: String = "",
)

/** A timetable period enriched with a live position relative to the current clock. */
data class LivePeriod(
    val startTime: String,
    val endTime: String,
    val subject: String,
    val room: String,
    val teacherName: String,
    val startMinutes: Int,
    val endMinutes: Int,
    /** -1 = finished, 0 = happening now, 1 = upcoming. Re-derived from the live clock. */
    val relation: Int,
)

/** A single unit the child's class covered on a given date (for "covered today"). */
data class CoveredUnit(
    val subject: String,
    val title: String,
    val coveredOn: String,
)

data class ParentDashboardState(
    // children + identity
    val children: List<DashboardChildSummary> = emptyList(),
    val selectedChildId: String? = null,
    val greeting: String = "",
    val alerts: List<DashboardAlertDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,

    // attendance
    val attendance: ParentAttendanceData? = null,
    val today: TodayAttendance = TodayAttendance(AttendanceDayState.NoData),
    val attendanceLoading: Boolean = false,

    // timetable (today + full week)
    val timetable: ParentTimetableData? = null,
    val todayPeriods: List<LivePeriod> = emptyList(),
    val timetableLoading: Boolean = false,

    // covered today (syllabus)
    val syllabus: ParentSyllabusData? = null,
    val coveredToday: List<CoveredUnit> = emptyList(),
    /** True once the school day is over → the "covered today" card shows its summary state. */
    val schoolDayEnded: Boolean = false,
    val syllabusLoading: Boolean = false,

    // marks (academics card)
    val latestMark: ParentMarkDto? = null,
    val previousMarkForSubject: ParentMarkDto? = null,
    /** Recent scored marks for the same subject as [latestMark], oldest→newest, for the sparkline. */
    val markTrend: List<Double> = emptyList(),
    val marksLoading: Boolean = false,

    // fees (fees card)
    val fees: FeeData? = null,
    val feesLoading: Boolean = false,
) {
    val selectedChild: DashboardChildSummary?
        get() = children.firstOrNull { it.id == selectedChildId } ?: children.firstOrNull()
}

class ParentDashboardViewModel(
    private val repository: ParentRepository,
    private val preferenceRepository: PreferenceRepository,
    private val selectedChildHolder: SelectedChildHolder,
) : ViewModel() {
    private val _state = MutableStateFlow(ParentDashboardState())
    val state: StateFlow<ParentDashboardState> = _state.asStateFlow()

    init {
        load()
        // React to a child picked on any other tab (RA-S05).
        viewModelScope.launch {
            selectedChildHolder.selectedChildId.collect { shared ->
                if (shared != null && shared != _state.value.selectedChildId) {
                    _state.update { it.copy(selectedChildId = shared) }
                    loadChildData(shared)
                }
            }
        }
    }

    private suspend fun token(): String? = preferenceRepository.getUserToken().first()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val token = token() ?: run {
                _state.update { it.copy(isLoading = false, error = "Not signed in") }
                return@launch
            }
            when (val result = repository.getDashboard(token)) {
                is NetworkResult.Success -> {
                    val data = result.data.data
                    val children = data.children.ifEmpty { listOfNotNull(data.childSummary) }
                    val sharedSel = selectedChildHolder.selectedChildId.value
                        ?.takeIf { id -> children.any { c -> c.id == id } }
                    val keep = sharedSel
                        ?: _state.value.selectedChildId?.takeIf { id -> children.any { c -> c.id == id } }
                    val resolved = keep ?: children.firstOrNull()?.id
                    selectedChildHolder.selectIfUnset(resolved)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            greeting = data.greeting,
                            alerts = data.alerts,
                            children = children,
                            selectedChildId = resolved,
                        )
                    }
                    resolved?.let { loadChildData(it) }
                }
                is NetworkResult.Error ->
                    _state.update { it.copy(isLoading = false, error = result.message) }
                is NetworkResult.ConnectionError ->
                    _state.update { it.copy(isLoading = false, error = "Connection error") }
            }
        }
    }

    /** Switch the active child — reactively re-drives the entire dashboard. */
    fun selectChild(childId: String) {
        if (childId == _state.value.selectedChildId) return
        _state.update {
            it.copy(
                selectedChildId = childId,
                // Clear the previous child's derived data so nothing stale flashes.
                attendance = null, today = TodayAttendance(AttendanceDayState.NoData),
                timetable = null, todayPeriods = emptyList(),
                syllabus = null, coveredToday = emptyList(),
                latestMark = null, previousMarkForSubject = null, markTrend = emptyList(),
                fees = null,
            )
        }
        selectedChildHolder.select(childId)
        loadChildData(childId)
    }

    /** Re-evaluate the live, clock-derived fields (current period, school-day-ended). */
    fun refreshLiveClock() {
        val tt = _state.value.timetable
        val syl = _state.value.syllabus
        _state.update {
            it.copy(
                todayPeriods = computeTodayPeriods(tt),
                schoolDayEnded = computeSchoolDayEnded(tt),
                coveredToday = computeCoveredToday(syl),
            )
        }
    }

    private fun loadChildData(childId: String) {
        loadAttendance(childId)
        loadTimetable(childId)
        loadSyllabus(childId)
        loadMarks(childId)
        loadFees(childId)
    }

    private fun loadFees(childId: String) {
        viewModelScope.launch {
            _state.update { it.copy(feesLoading = true) }
            val token = token() ?: run { _state.update { it.copy(feesLoading = false) }; return@launch }
            when (val r = repository.getFees(token, childId)) {
                is NetworkResult.Success -> _state.update { it.copy(feesLoading = false, fees = r.data.data) }
                else -> _state.update { it.copy(feesLoading = false) }
            }
        }
    }

    private fun loadAttendance(childId: String) {
        viewModelScope.launch {
            _state.update { it.copy(attendanceLoading = true) }
            val token = token() ?: run { _state.update { it.copy(attendanceLoading = false) }; return@launch }
            when (val r = repository.getChildAttendance(token, childId)) {
                is NetworkResult.Success -> _state.update {
                    val data = r.data.data
                    it.copy(
                        attendanceLoading = false,
                        attendance = data,
                        today = resolveToday(data),
                    )
                }
                else -> _state.update { it.copy(attendanceLoading = false) }
            }
        }
    }

    private fun loadTimetable(childId: String) {
        viewModelScope.launch {
            _state.update { it.copy(timetableLoading = true) }
            val token = token() ?: run { _state.update { it.copy(timetableLoading = false) }; return@launch }
            when (val r = repository.getChildTimetable(token, childId)) {
                is NetworkResult.Success -> _state.update {
                    val data = r.data.data
                    it.copy(
                        timetableLoading = false,
                        timetable = data,
                        todayPeriods = computeTodayPeriods(data),
                        schoolDayEnded = computeSchoolDayEnded(data),
                    )
                }
                else -> _state.update { it.copy(timetableLoading = false) }
            }
        }
    }

    private fun loadSyllabus(childId: String) {
        viewModelScope.launch {
            _state.update { it.copy(syllabusLoading = true) }
            val token = token() ?: run { _state.update { it.copy(syllabusLoading = false) }; return@launch }
            when (val r = repository.getChildSyllabus(token, childId)) {
                is NetworkResult.Success -> _state.update {
                    val data = r.data.data
                    it.copy(
                        syllabusLoading = false,
                        syllabus = data,
                        coveredToday = computeCoveredToday(data),
                    )
                }
                else -> _state.update { it.copy(syllabusLoading = false) }
            }
        }
    }

    private fun loadMarks(childId: String) {
        viewModelScope.launch {
            _state.update { it.copy(marksLoading = true) }
            val token = token() ?: run { _state.update { it.copy(marksLoading = false) }; return@launch }
            when (val r = repository.getChildMarks(token, childId)) {
                is NetworkResult.Success -> _state.update {
                    val results = r.data.data.results
                    // Latest = first entry that has a real score for this child (server orders DESC).
                    val scored = results.filter { m -> m.marks != null }
                    val latest = scored.firstOrNull()
                    // Find an earlier mark in the same subject so we can show "+N vs last".
                    val prev = if (latest != null) {
                        scored.drop(1).firstOrNull { m -> m.subject == latest.subject }
                    } else null
                    // Build the sparkline trend: same-subject scores as a percentage of max,
                    // server orders DESC so reverse to oldest→newest. Capped to the last 8.
                    val trend = if (latest != null) {
                        scored.filter { m -> m.subject == latest.subject && m.marks != null && m.maxMarks > 0 }
                            .map { m -> (m.marks!! / m.maxMarks) * 100.0 }
                            .take(8)
                            .reversed()
                    } else emptyList()
                    it.copy(marksLoading = false, latestMark = latest, previousMarkForSubject = prev, markTrend = trend)
                }
                else -> _state.update { it.copy(marksLoading = false) }
            }
        }
    }

    // ── Pure derivations (testable, clock-driven) ─────────────────────────────

    /**
     * Resolve TODAY's attendance state for the child against the real records + holidays.
     * Precedence (LAW: every state handled correctly):
     *   1. an explicit record for today (present/absent/late) always wins
     *   2. a declared holiday/vacation for today (dated holiday, or weekly/recurring rule)
     *   3. Sunday (weekday 7) → non-school day even without an explicit rule
     *   4. otherwise NoData (e.g. attendance not yet marked this morning)
     */
    private fun resolveToday(data: ParentAttendanceData): TodayAttendance {
        val iso = todayIso()
        // 1) explicit record.
        data.records.firstOrNull { it.date == iso }?.let { rec ->
            return when (rec.status.lowercase()) {
                "present" -> TodayAttendance(AttendanceDayState.Present)
                "late" -> TodayAttendance(AttendanceDayState.Late)
                "absent" -> TodayAttendance(AttendanceDayState.Absent)
                else -> TodayAttendance(AttendanceDayState.NoData)
            }
        }
        // 2) declared holiday/vacation.
        holidayForDate(iso, data.holidays)?.let { h ->
            val isVacation = h.title.contains("vacation", ignoreCase = true) ||
                h.title.contains("break", ignoreCase = true)
            return TodayAttendance(
                state = if (isVacation) AttendanceDayState.Vacation else AttendanceDayState.Holiday,
                label = h.title,
            )
        }
        // 3) Sunday.
        if (todayWeekday() == 7) return TodayAttendance(AttendanceDayState.Sunday)
        // 4) nothing yet.
        return TodayAttendance(AttendanceDayState.NoData)
    }

    /**
     * Returns the holiday rule that applies to [iso], if any:
     *   - a dated holiday whose `date == iso`
     *   - a weekly recurring rule (e.g. Sundays) — matched by weekday
     */
    private fun holidayForDate(iso: String, holidays: List<ParentHolidayDto>): ParentHolidayDto? {
        holidays.firstOrNull { it.date == iso }?.let { return it }
        val (y, m, d) = parseIsoDate(iso) ?: return null
        val dow = dayOfWeek(y, m, d) // 0=Sun..6=Sat
        return holidays.firstOrNull { h ->
            h.frequency.equals("weekly", ignoreCase = true) &&
                // weekly rules carry the recurring weekday in the title (e.g. "Sunday")
                weekdayMatchesTitle(dow, h.title)
        }
    }

    private fun weekdayMatchesTitle(dow: Int, title: String): Boolean {
        val name = when (dow) {
            0 -> "sunday"; 1 -> "monday"; 2 -> "tuesday"; 3 -> "wednesday"
            4 -> "thursday"; 5 -> "friday"; 6 -> "saturday"; else -> ""
        }
        return title.contains(name, ignoreCase = true)
    }

    /** Today's periods with a live relation flag against the current wall clock. */
    private fun computeTodayPeriods(tt: ParentTimetableData?): List<LivePeriod> {
        tt ?: return emptyList()
        val wd = todayWeekday()
        val periods = tt.weekdays.firstOrNull { it.weekday == wd }?.periods ?: return emptyList()
        val now = nowMinutesOfDay()
        return periods.map { p -> p.toLive(now) }.sortedBy { it.startMinutes }
    }

    /** The school day has ended once now is past the last period's end time today. */
    private fun computeSchoolDayEnded(tt: ParentTimetableData?): Boolean {
        tt ?: return false
        val wd = todayWeekday()
        val periods = tt.weekdays.firstOrNull { it.weekday == wd }?.periods ?: return false
        val lastEnd = periods.mapNotNull { parseHourMinute(it.endTime) }.maxOrNull() ?: return false
        return nowMinutesOfDay() > lastEnd
    }

    /** Units the child's class covered TODAY (coveredOn == today), across all subjects. */
    private fun computeCoveredToday(syl: ParentSyllabusData?): List<CoveredUnit> {
        syl ?: return emptyList()
        val iso = todayIso()
        return syl.subjects.flatMap { subj ->
            subj.units.filter { u -> u.isCovered && u.coveredOn == iso }
                .map { u -> CoveredUnit(subject = subj.subject, title = u.title, coveredOn = u.coveredOn ?: iso) }
        }
    }
}

/** Enrich a raw period with live clock relation. */
private fun ParentPeriodDto.toLive(nowMinutes: Int): LivePeriod {
    val s = parseHourMinute(startTime) ?: 0
    val e = parseHourMinute(endTime) ?: (s + 40)
    val relation = when {
        nowMinutes >= e -> -1     // finished
        nowMinutes in s until e -> 0  // now
        else -> 1                 // upcoming
    }
    return LivePeriod(
        startTime = startTime,
        endTime = endTime,
        subject = subject,
        room = room,
        teacherName = teacherName,
        startMinutes = s,
        endMinutes = e,
        relation = relation,
    )
}
