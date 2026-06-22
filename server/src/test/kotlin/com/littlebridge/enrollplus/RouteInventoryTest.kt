package com.littlebridge.enrollplus

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Source-level guard for the P0 regressions documented in
 * SCHOOL_SIDE_STATUS_REPORT.md §5.1 and §5.3.
 *
 * These are intentionally static-source assertions (not a live HTTP routing
 * walk) so they run with zero DB/network setup and fail fast in CI the moment
 * someone re-introduces:
 *   - the unresolved Exposed `inList` import that broke `:server:compileKotlin`
 *   - the legacy mock parent `/track-progress` and `/fees` routes that shadow
 *     the live DB-backed owners (TrackProgressRouting / ParentFeesRouting).
 *
 * It also asserts the live owners and the new teacher-assignments route exist,
 * so a future refactor cannot silently drop them.
 */
class RouteInventoryTest {

    private val serverMain = File("src/main/kotlin/com.littlebridge.enrollplus")

    private fun source(relative: String): String {
        val f = File(serverMain, relative)
        assertTrue(f.exists(), "Expected source file to exist: ${f.path}")
        return f.readText()
    }

    @Test
    fun parentRouting_hasNoUnresolvedInListImport() {
        val src = source("feature/user/ParentRouting.kt")
        assertFalse(
            src.contains("org.jetbrains.exposed.sql.inList"),
            "ParentRouting.kt must NOT import the unresolved 'inList' (P0 compile blocker, report §5.1)"
        )
    }

    @Test
    fun parentRouting_hasNoLegacyMockRoutes() {
        val src = source("feature/user/ParentRouting.kt")
        assertFalse(
            src.contains("\"/track-progress\""),
            "Legacy mock /track-progress route must stay removed from ParentRouting.kt (report §5.3)"
        )
        assertFalse(
            src.contains("\"/fees\""),
            "Legacy mock /fees route must stay removed from ParentRouting.kt (report §5.3)"
        )
    }

    @Test
    fun liveParentRouteOwnersExist() {
        assertTrue(
            File(serverMain, "feature/parent/TrackProgressRouting.kt").exists(),
            "TrackProgressRouting.kt (live /parent/track-progress owner) must exist"
        )
        assertTrue(
            File(serverMain, "feature/parent/ParentFeesRouting.kt").exists(),
            "ParentFeesRouting.kt (live /parent/fees owner) must exist"
        )
    }

    @Test
    fun teacherAssignmentRouteIsRegistered() {
        val app = File(serverMain, "Application.kt").readText()
        assertTrue(
            app.contains("teacherAssignmentRouting()"),
            "teacherAssignmentRouting() must be registered in Application.kt"
        )
        assertTrue(
            File(serverMain, "feature/school/TeacherAssignmentRouting.kt").exists(),
            "TeacherAssignmentRouting.kt must exist"
        )
    }
}
