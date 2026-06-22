package com.littlebridge.enrollplus.core.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RA-S05 — a single, app-scoped source of truth for the parent's currently
 * selected child.
 *
 * Before this, each parent ViewModel (`ParentHomeViewModel`,
 * `ParentAcademicsViewModel`, `ParentLeaveViewModel`, `FeeViewModel`) owned its
 * own `selectedChildId`, so switching the active child on one tab did not change
 * the others — a parent with two children could see Child B's hero on Home but
 * Child A's marks/fees on Academics/Fees.
 *
 * This holder is registered as a Koin `single`, so every parent ViewModel shares
 * the exact same instance. ViewModels observe [selectedChildId] and publish the
 * user's pick via [select]. It deliberately holds only the id (a `String?`) —
 * the per-child data still comes from each feature's own endpoint, scoped by the
 * id. Cleared on logout via [clear] so a re-login as a different parent starts
 * fresh.
 */
class SelectedChildHolder {
    private val _selectedChildId = MutableStateFlow<String?>(null)

    /** The currently selected child id, shared across all parent ViewModels. */
    val selectedChildId: StateFlow<String?> = _selectedChildId.asStateFlow()

    /** Publish the parent's child selection. No-op if unchanged. */
    fun select(childId: String?) {
        if (_selectedChildId.value != childId) {
            _selectedChildId.value = childId
        }
    }

    /**
     * Seed the selection only when nothing is chosen yet — used by whichever tab
     * loads the children list first, so the default (first child) flows to the
     * other tabs without clobbering an explicit user pick.
     */
    fun selectIfUnset(childId: String?) {
        if (_selectedChildId.value == null && childId != null) {
            _selectedChildId.value = childId
        }
    }

    /** Reset on logout / parent switch. */
    fun clear() {
        _selectedChildId.value = null
    }
}
