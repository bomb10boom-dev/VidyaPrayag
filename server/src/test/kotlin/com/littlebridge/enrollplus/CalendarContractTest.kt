package com.littlebridge.enrollplus

import com.littlebridge.enrollplus.feature.school.CalendarSummary
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Contract test guarding the regression from SCHOOL_SIDE_STATUS_REPORT.md §4.2.
 *
 * The Academic Calendar crashed on the phone with:
 *   "Field 'working_days' is required ... CalendarSummaryDto ... missing at path: $.data.summary"
 *
 * Root cause was backend/client DTO drift: the server stopped emitting
 * `working_days`. This test fails fast if any future change drops either the
 * canonical `working_days` field or its backward-compatible `total_working_days`
 * alias from the serialized payload, so the regression can never silently
 * return.
 */
class CalendarContractTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun calendarSummary_alwaysEmits_workingDays_and_alias() {
        val summary = CalendarSummary(
            workingDays = 22,
            totalWorkingDays = 22,
            publicHolidays = 3,
            schoolHolidays = 5
        )
        val encoded = json.encodeToString(CalendarSummary.serializer(), summary)

        assertTrue(
            encoded.contains("\"working_days\""),
            "CalendarSummary MUST serialize 'working_days' (client requires it). Got: $encoded"
        )
        assertTrue(
            encoded.contains("\"total_working_days\""),
            "CalendarSummary MUST serialize 'total_working_days' alias. Got: $encoded"
        )
        assertTrue(encoded.contains("\"public_holidays\""), "Missing public_holidays. Got: $encoded")
        assertTrue(encoded.contains("\"school_holidays\""), "Missing school_holidays. Got: $encoded")
    }

    @Test
    fun calendarSummary_decodesWhenBothFieldsPresent() {
        val payload = """
            {"working_days":20,"total_working_days":20,"public_holidays":2,"school_holidays":4}
        """.trimIndent()
        val decoded = json.decodeFromString(CalendarSummary.serializer(), payload)
        assertTrue(decoded.workingDays == 20 && decoded.totalWorkingDays == 20)
    }
}
