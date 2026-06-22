package com.littlebridge.enrollplus.di

import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.schools.data.remote.KtorSchoolApi
import com.littlebridge.enrollplus.feature.schools.data.repository.SchoolRepositoryImpl
import com.littlebridge.enrollplus.feature.schools.domain.repository.SchoolRepository
import com.littlebridge.enrollplus.feature.schools.domain.usecase.GetSchoolsUseCase
import com.littlebridge.enrollplus.presentation.MainViewModel
import com.littlebridge.enrollplus.feature.parent.presentation.FeeViewModel
import com.littlebridge.enrollplus.feature.parent.presentation.ScholarshipsViewModel
import com.littlebridge.enrollplus.feature.parent.presentation.ParentAnnouncementViewModel
import com.littlebridge.enrollplus.feature.parent.presentation.NotificationsViewModel
import com.littlebridge.enrollplus.feature.parent.presentation.LinkChildViewModel
import com.littlebridge.enrollplus.feature.parent.presentation.ParentHomeViewModel
import com.littlebridge.enrollplus.feature.parent.presentation.ParentProfileViewModel
import com.littlebridge.enrollplus.feature.parent.presentation.TrackProgressViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.SchoolDashboardViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.InstitutionalBasicOBViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.BrandingInfoOBViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.AcademicInfoOBViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.LaunchInfoOBViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.InstitutionalProfileViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.AdmissionCRMViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.SchoolAnnouncementsViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.MessagesViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.SchedulePTMViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.AcademicCalendarViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.LeaveRequestsViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.DailyAttendanceViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.AnalyticsDashboardViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.StudentAnalyticsViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.TeacherPerformanceViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.ClassPerformanceViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.SyllabusCoverageViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.ResultsViewModel
import com.littlebridge.enrollplus.util.AppConfig
import com.littlebridge.enrollplus.util.AppLogger
import com.littlebridge.enrollplus.core.network.buildRefreshClient
import com.littlebridge.enrollplus.core.network.clearBearerCache
import com.littlebridge.enrollplus.core.network.installTokenAuth
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val commonModule = module {
    // Remote
    single {
        val prefs: PreferenceRepository = get()
        // Plain client (NO Auth plugin) used solely to perform the refresh-token
        // exchange, so the bearer refresh path never recurses through itself.
        val refreshClient = buildRefreshClient(get())

        // Forward reference to the authenticated client so the refresh-failure
        // logout path can evict its in-memory bearer-token cache. Assigned right
        // after the client is built, before any request can run.
        lateinit var authedClient: HttpClient

        authedClient = HttpClient(get()) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }

            install(HttpRedirect) {
                checkHttpMethod = false
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 60000
                connectTimeoutMillis = 60000
                socketTimeoutMillis = 60000
            }

            // Auth: attach the stored access token to every request and, on a
            // 401, automatically exchange the persisted refresh token for a new
            // access token (audit §3.4, finding F). On refresh failure the user
            // is logged out cleanly. Implemented in TokenAuthenticator.kt — this
            // call is the only wiring needed; every API call through this client
            // gets transparent 401 refresh + retry.
            installTokenAuth(
                prefs = prefs,
                refreshClient = refreshClient,
                onRefreshFailed = {
                    // Reuse the existing logout primitives: clear the persisted
                    // session FIRST so loadTokens() reads null on the next request,
                    // then evict the Auth plugin's cached bearer token. The
                    // reactive authState (App.kt observes getUserToken()) sees the
                    // null token and navigates back to landing.
                    prefs.clearSession()
                    authedClient.clearBearerCache()
                },
            )
        }
        authedClient
    }
    // RA-S01: session-manager wraps the singleton HttpClient so logout can evict
    // the Ktor Auth plugin's in-memory bearer-token cache (clearToken()).
    single { com.littlebridge.enrollplus.core.network.SessionManager(get()) }
    single { KtorSchoolApi(get(), AppConfig.schoolBaseUrl) }
    single { 
        com.littlebridge.enrollplus.feature.content.data.remote.ContentApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single { 
        com.littlebridge.enrollplus.feature.auth.data.remote.AuthApi(
            client = get(),
            baseUrl = AppConfig.authBaseUrl
        ) 
    }
    single {
        com.littlebridge.enrollplus.feature.parent.data.remote.ParentApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.OnboardingApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.AdmissionApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.MessagesApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.AnnouncementsApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.TeachersApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    // RA-47: institutional-profile (schools row) read/edit
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.SchoolProfileApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    // RA-45: student roster + student/teacher profile detail
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.StudentsApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    // RA-TAM: Teacher Assignment Management (reusable assignment module)
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.TeacherAssignmentApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    // RA-S17: Non-teaching-staff vertical
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.StaffApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    // RA-52: admin Records rollups (attendance / marks / fees)
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.RecordsApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.PtmApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.CalendarApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    // VP-CAL: Academic Calendar platform + Academic Year management
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.AcademicCalendarPlatformApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.AcademicYearApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.AttendanceApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.LeaveRequestsApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    // RA-48: school-admin link-request queue API.
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.LinkRequestsApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.AnalyticsApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    // Redesigned admin home dashboard (summary / analytics / activity).
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.AdminDashboardApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.ResultsApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.UserProfileApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    single {
        com.littlebridge.enrollplus.feature.admin.data.remote.MediaApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    // Teacher vertical (master doc G1)
    single {
        com.littlebridge.enrollplus.feature.teacher.data.remote.TeacherApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }
    // Notification FOUNDATION (push infra) — device-token registration client.
    // The admin broadcast endpoint is server-only, so the client surface is a
    // single POST /api/device-tokens call invoked by the Android token registrar.
    single {
        com.littlebridge.enrollplus.feature.notification.data.remote.NotificationApi(
            client = get(),
            baseUrl = AppConfig.schoolBaseUrl
        )
    }

    // Repositories
    single<SchoolRepository> { SchoolRepositoryImpl(get(), get()) }
    single<com.littlebridge.enrollplus.feature.content.domain.repository.ContentRepository> { 
        com.littlebridge.enrollplus.feature.content.data.repository.ContentRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.auth.domain.repository.AuthRepository> { 
        // RA-S05: 4th arg = SelectedChildHolder (cleared on logout).
        com.littlebridge.enrollplus.feature.auth.data.repository.AuthRepositoryImpl(get(), get(), get(), get())
    }
    single<com.littlebridge.enrollplus.feature.parent.domain.repository.ParentRepository> {
        com.littlebridge.enrollplus.feature.parent.data.repository.ParentRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.OnboardingRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.OnboardingRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.AdmissionRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.AdmissionRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.MessagesRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.MessagesRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.AnnouncementsRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.AnnouncementsRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.TeachersRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.TeachersRepositoryImpl(get())
    }
    // RA-47
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.SchoolProfileRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.SchoolProfileRepositoryImpl(get())
    }
    // RA-45
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.StudentsRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.StudentsRepositoryImpl(get())
    }
    // RA-TAM: Teacher Assignment Management repository
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.TeacherAssignmentRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.TeacherAssignmentRepositoryImpl(get())
    }
    // RA-S17
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.StaffRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.StaffRepositoryImpl(get())
    }
    // RA-52
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.RecordsRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.RecordsRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.PtmRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.PtmRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.CalendarRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.CalendarRepositoryImpl(get())
    }
    // VP-CAL: Academic Calendar platform + Academic Year management repositories
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.AcademicCalendarPlatformRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.AcademicCalendarPlatformRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.AcademicYearRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.AcademicYearRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.AttendanceRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.AttendanceRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.LeaveRequestsRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.LeaveRequestsRepositoryImpl(get())
    }
    // RA-48: school-admin link-request queue repository.
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.LinkRequestsRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.LinkRequestsRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.AnalyticsRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.AnalyticsRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.AdminDashboardRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.AdminDashboardRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.ResultsRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.ResultsRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.admin.domain.repository.UserProfileRepository> {
        com.littlebridge.enrollplus.feature.admin.data.repository.UserProfileRepositoryImpl(get())
    }
    single<com.littlebridge.enrollplus.feature.teacher.domain.repository.TeacherRepository> {
        com.littlebridge.enrollplus.feature.teacher.data.repository.TeacherRepositoryImpl(get())
    }
    // Notification FOUNDATION repository — delegates to NotificationApi.
    single<com.littlebridge.enrollplus.feature.notification.domain.repository.NotificationRepository> {
        com.littlebridge.enrollplus.feature.notification.data.repository.NotificationRepositoryImpl(get())
    }

    // UseCases
    factory { GetSchoolsUseCase(get()) }
}

val viewModelModule = module {
    factory { MainViewModel(get(), get(), get(), get()) }
    factory { com.littlebridge.enrollplus.presentation.PermissionViewModel(get(), get()) }
    // Schools-discovery marketplace VM — drives DiscoveryScreenV2 off the real
    // GET /api/v1/parent/schools/discover endpoint. See BACKEND_GAPS.md §3.
    factory {
        com.littlebridge.enrollplus.feature.schools.presentation.SchoolDiscoveryViewModel(
            get(),  // KtorSchoolApi
            get(),  // PreferenceRepository
        )
    }
    // NOTE (SWEEP-B): the following V1 parent VMs were registered here but had
    // ZERO injection/usage anywhere after the V1->V2 screen migration
    // (ParentDashboardViewModel superseded by ParentHomeViewModel + SchoolDiscoveryViewModel;
    //  ChildBasicInfo/YourPreferences/LocationRequest/CareerPath/DailyStatus/ParentReports/
    //  ParentSchedulePTM/ParentMessage screens dropped in V2). Their dead factory
    // registrations were removed to keep the Koin graph clean — every remaining
    // registration below is actually consumed via koinViewModel().
    // RA-S05: a single, app-scoped holder so every parent tab shares the same
    // selected-child id (switching the child on one tab updates all of them).
    single { com.littlebridge.enrollplus.core.state.SelectedChildHolder() }
    factory { FeeViewModel(get(), get(), get()) }
    factory { ScholarshipsViewModel(get(), get()) }
    factory { ParentAnnouncementViewModel(get(), get()) }
    factory { NotificationsViewModel(get(), get()) }
    factory { LinkChildViewModel(get(), get()) }
    factory { ParentHomeViewModel(get(), get(), get()) }
    factory { ParentProfileViewModel(get(), get()) }
    factory { TrackProgressViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.parent.presentation.ParentAcademicsViewModel(get(), get(), get()) }
    factory { com.littlebridge.enrollplus.feature.parent.presentation.ParentDashboardViewModel(get(), get(), get()) }
    factory { com.littlebridge.enrollplus.feature.parent.presentation.ParentLeaveViewModel(get(), get(), get()) }
    factory { com.littlebridge.enrollplus.feature.parent.presentation.ParentMessageViewModel(get(), get()) }
    factory { SchoolDashboardViewModel(get(), get(), get()) }
    factory { InstitutionalBasicOBViewModel(get(), get()) }
    factory { BrandingInfoOBViewModel(get(), get(), get()) }
    factory { AcademicInfoOBViewModel(get(), get()) }
    // Onboarding-time teacher provisioning (creates REAL loginable teacher
    // accounts via TeachersRepository → POST /school/teachers).
    factory { com.littlebridge.enrollplus.feature.admin.presentation.TeacherProvisioningOBViewModel(get(), get()) }
    factory { LaunchInfoOBViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.admin.presentation.OnboardingGateViewModel(get(), get()) }
    factory { InstitutionalProfileViewModel(get(), get(), get()) }
    factory { com.littlebridge.enrollplus.feature.admin.presentation.SchoolProfileViewModel(get(), get()) } // RA-47
    factory { com.littlebridge.enrollplus.feature.admin.presentation.StudentRosterViewModel(get(), get()) } // RA-45
    factory { com.littlebridge.enrollplus.feature.admin.presentation.StaffViewModel(get(), get()) } // RA-S17
    factory { com.littlebridge.enrollplus.feature.admin.presentation.StudentProfileViewModel(get(), get()) } // RA-45
    factory { com.littlebridge.enrollplus.feature.admin.presentation.TeacherProfileViewModel(get(), get(), get()) } // RA-45 (+RA-S17 delete-in-profile)
    factory { com.littlebridge.enrollplus.feature.admin.presentation.TeacherAssignmentViewModel(get(), get()) } // RA-TAM: reusable assignment module
    factory { com.littlebridge.enrollplus.feature.admin.presentation.SchoolRecordsViewModel(get(), get()) } // RA-52
    factory { AdmissionCRMViewModel(get(), get()) }
    factory { SchoolAnnouncementsViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.admin.presentation.SchoolTeachersViewModel(get(), get()) }
    factory { MessagesViewModel(get(), get(), get()) }
    factory { SchedulePTMViewModel(get(), get()) }
    factory { AcademicCalendarViewModel(get(), get()) }
    // VP-CAL: premium Academic Calendar platform + 7-step create-event wizard + Academic Year mgmt
    factory { com.littlebridge.enrollplus.feature.admin.presentation.AcademicCalendarPlatformViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.admin.presentation.CreateCalendarEventViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.admin.presentation.AcademicYearViewModel(get(), get()) }
    factory { LeaveRequestsViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.admin.presentation.LinkRequestsViewModel(get(), get()) }
    factory { DailyAttendanceViewModel(get(), get()) }
    factory { AnalyticsDashboardViewModel(get(), get()) }
    factory { StudentAnalyticsViewModel(get(), get()) }
    factory { TeacherPerformanceViewModel(get(), get()) }
    factory { ClassPerformanceViewModel(get(), get()) }
    factory { SyllabusCoverageViewModel(get(), get()) }
    factory { ResultsViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.content.presentation.LandingViewModel(get()) }
    factory { com.littlebridge.enrollplus.feature.auth.presentation.AuthViewModel(get()) }
    // Teacher vertical (master doc G1) — 7 VMs, all (TeacherRepository, PreferenceRepository)
    factory { com.littlebridge.enrollplus.feature.teacher.presentation.TeacherHomeViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.teacher.presentation.TeacherClassesViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.teacher.presentation.TeacherAttendanceViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.teacher.presentation.TeacherMarksViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.teacher.presentation.TeacherAssessmentsViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.teacher.presentation.TeacherSyllabusViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.teacher.presentation.TeacherHomeworkViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.teacher.presentation.TeacherProfileViewModel(get(), get()) }
    factory { com.littlebridge.enrollplus.feature.teacher.presentation.TeacherLeaveViewModel(get(), get()) }
}

fun initKoin(
    appDeclaration: KoinAppDeclaration = {}
) = startKoin {
    appDeclaration()
    modules(commonModule, viewModelModule, platformModule())
}

// For iOS
fun initKoin() = initKoin {}
