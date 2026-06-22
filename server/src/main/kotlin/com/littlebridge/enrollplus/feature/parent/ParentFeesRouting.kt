/*
 * File: ParentFeesRouting.kt
 * Module: feature.parent
 *
 * Endpoint: GET /api/v1/parent/fees   (JWT)
 *
 * Spec ref: parent_api_spec.artifact.md §Module: School Management §Screen: Fees
 *
 * Aggregates the parent's fee_records into the stats block expected by the
 * UI and appends fee-related announcements from a SCHOOL-SCOPED CMS key
 * app_config["parent_fees_announcements:<schoolId>"] (RA-26), falling back to
 * the legacy global key app_config["parent_fees_announcements"] and then to a
 * static default (so ops can edit deadlines without redeploying, and each
 * school can publish its own copy).
 *
 * Aggregation rules:
 *   total_collected = SUM(amount WHERE status = 'PAID')
 *   outstanding     = SUM(amount WHERE status IN ('DUE','OVERDUE'))
 *   overdue_count   = COUNT(* WHERE status = 'OVERDUE')
 *   progress        = total_collected / (total_collected + outstanding)
 *                     (returns 0.0 when no rows exist; coerced to [0,1])
 */
package com.littlebridge.enrollplus.feature.parent

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.principalUserId
import com.littlebridge.enrollplus.db.AppConfigTable
import com.littlebridge.enrollplus.db.ChildrenTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.FeeRecordsTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

/**
 * Response DTO that MUST stay field-for-field identical to the client model
 * `shared/.../parent/domain/model/ParentFeatureModels.kt#FeeData`. The client
 * wraps this in `FeeResponse{success, data: FeeData}` and deserializes against
 * the canonical `{success, message, data}` envelope, so `data` here is exactly
 * `FeeData`. Any drift causes a kotlinx.serialization MissingFieldException and
 * crashes the Parent Fees tab on open (VM auto-loads in init).
 */
@Serializable
data class FeesAnnouncement(
    val id: String,
    val title: String,
    val time: String,
    val description: String,
    @SerialName("open_rate") val openRate: String,
    val engagement: String,
    val type: String
)

@Serializable
data class ParentFeesResponse(
    @SerialName("total_collected") val totalCollected: String,
    @SerialName("collection_progress") val collectionProgress: Float,
    @SerialName("outstanding_fees") val outstandingFees: String,
    @SerialName("overdue_count") val overdueCount: Int,
    val announcements: List<FeesAnnouncement>
)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun money(amount: Double, currency: String): String {
    // RA-25: India-first product — default to ₹ (INR), matching the
    // FeeRecordsTable.currency column default. Other codes still map correctly.
    val symbol = when (currency.uppercase()) {
        "USD" -> "$"
        "INR" -> "₹"
        "EUR" -> "€"
        "GBP" -> "£"
        else  -> "₹"
    }
    return "$symbol${"%,d".format(amount.toLong())}"
}

fun Route.parentFeesRouting() {
    authenticate("jwt") {
        route("/api/v1/parent") {
            get("/fees") {
                val uid = call.principalUserId()?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }

                // RA-S05: optional ?child_id= scopes the fee aggregation to a
                // single child. Parsed defensively (a malformed id is ignored,
                // not an error). Filtering is applied in-memory after the
                // parent-scoped fetch to stay Postgres-portable (no extra WHERE
                // that would need an index) and to keep tenancy on parentId.
                val childIdFilter = call.request.queryParameters["child_id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

                val response = dbQuery {
                    val rows = FeeRecordsTable.selectAll()
                        .where { FeeRecordsTable.parentId eq uid }
                        .toList()
                        // RA-S05: when a child is selected, only that child's records
                        // count toward the stats. Records with a null child_id
                        // (school-wide / unassigned) are excluded from a per-child view.
                        .let { all ->
                            if (childIdFilter == null) all
                            else all.filter { it[FeeRecordsTable.childId] == childIdFilter }
                        }

                    // RA-25: fall back to INR (India-first) when the parent has no
                    // fee record yet, instead of USD.
                    val currency = rows.firstOrNull()?.get(FeeRecordsTable.currency) ?: "INR"
                    val collected = rows.filter { it[FeeRecordsTable.status] == "PAID" }
                        .sumOf { it[FeeRecordsTable.amount] }
                    val outstanding = rows.filter { it[FeeRecordsTable.status] in setOf("DUE", "OVERDUE") }
                        .sumOf { it[FeeRecordsTable.amount] }
                    val overdueCount = rows.count { it[FeeRecordsTable.status] == "OVERDUE" }

                    val total = collected + outstanding
                    val progress = if (total <= 0.0) 0f
                                   else (collected / total).coerceIn(0.0, 1.0).toFloat()

                    // ----- announcements (CMS, school-scoped — RA-26) -----
                    // Resolve the parent's school from their first active child,
                    // then prefer a school-scoped CMS key so each school can
                    // publish its OWN fee announcements. Fall back to the legacy
                    // global key, then to a static default, so older configs and
                    // parents without a linked school still get sensible copy.
                    val schoolId = ChildrenTable.selectAll()
                        .where { (ChildrenTable.parentId eq uid) and (ChildrenTable.isActive eq true) }
                        .orderBy(ChildrenTable.createdAt, SortOrder.ASC)
                        .firstOrNull()
                        ?.get(ChildrenTable.schoolId)

                    fun readConfig(key: String): String? = AppConfigTable.selectAll()
                        .where { AppConfigTable.key eq key }
                        .singleOrNull()
                        ?.get(AppConfigTable.value)

                    val annRaw = (schoolId?.let { readConfig("parent_fees_announcements:$it") })
                        ?: readConfig("parent_fees_announcements")
                    val announcements: List<FeesAnnouncement> = annRaw?.let {
                        runCatching {
                            lenientJson.decodeFromString(ListSerializer(FeesAnnouncement.serializer()), it)
                        }.getOrNull()
                    } ?: listOf(
                        FeesAnnouncement(
                            id = "f1",
                            title = "Deadline",
                            time = "2h ago",
                            description = "Submit Q3 fees.",
                            openRate = "0%",
                            engagement = "0",
                            type = "Payment"
                        )
                    )

                    ParentFeesResponse(
                        totalCollected = money(collected, currency),
                        collectionProgress = progress,
                        outstandingFees = money(outstanding, currency),
                        overdueCount = overdueCount,
                        announcements = announcements
                    )
                }

                call.ok(response, message = "Fee status fetched successfully")
            }
        }
    }
}
