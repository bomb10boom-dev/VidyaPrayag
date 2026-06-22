package com.littlebridge.enrollplus.ui.v2.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.littlebridge.enrollplus.ui.v2.theme.VTheme

/**
 * VPullRefresh — the single pull-to-refresh wrapper (FEATURE 7).
 *
 * Thin wrapper over Material3's [PullToRefreshBox] so every scrollable list screen gets the same
 * gesture + the same brand-tinted indicator with one call. RULE-1: the indicator is tinted with
 * the existing primary brand token ([VTheme.colors.tealDeep]) and drawn on the existing surface
 * token ([VTheme.colors.card]) — no new colours. RULE-3: reuse this instead of re-deriving the
 * state/indicator at every callsite.
 *
 * Wire `isRefreshing` to the ViewModel's "refresh in flight" flag and `onRefresh` to its refresh
 * action. When the refresh completes and the list re-populates, pair the content with the
 * FEATURE 5 `staggeredItemEntrance` so the rows re-enter with the ladder.
 *
 * RULE-5 (CMP-safe): `androidx.compose.material3.pulltorefresh.*` is part of the Compose
 * Multiplatform material3 artifact already on the classpath — commonMain-compatible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = VTheme.colors
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = c.card,
                color = c.tealDeep,
            )
        },
    ) {
        // PullToRefreshBox is itself a Box; the trailing content lambda is the scrollable body.
        Box { content() }
    }
}
