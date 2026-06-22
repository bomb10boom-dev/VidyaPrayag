package com.littlebridge.enrollplus.feature.parent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.parent.domain.repository.ParentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI-facing notification item. Mirrors the server [ParentNotificationDto] shape. */
data class NotificationItem(
    val id: String,
    val category: String, // "fees" | "academic" | "attendance" | "announcement"
    val title: String,
    val body: String,
    val time: String,
    val unread: Boolean,
)

data class NotificationsState(
    val notifications: List<NotificationItem> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * NotificationsViewModel — drives [NotificationsScreenV2] off the real
 * `GET /api/v1/parent/notifications` feed (report §5.3, SWEEP-A). Replaces the
 * MockV2.notifications source the screen used to default to.
 */
class NotificationsViewModel(
    private val repository: ParentRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(isLoading = false, error = "Not signed in") }
                return@launch
            }
            when (val result = repository.getNotifications(token)) {
                is NetworkResult.Success -> {
                    val data = result.data.data
                    _state.update {
                        it.copy(
                            isLoading = false,
                            notifications = data.notifications.map { n ->
                                NotificationItem(
                                    id = n.id,
                                    category = n.category,
                                    title = n.title,
                                    body = n.body,
                                    time = n.time,
                                    unread = n.unread,
                                )
                            },
                            unreadCount = data.unreadCount,
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                is NetworkResult.ConnectionError -> {
                    _state.update { it.copy(isLoading = false, error = "Connection error") }
                }
            }
        }
    }

    /**
     * RA-46: persist "mark all read" on the server (optimistic UI first, then
     * the real PATCH so the bell count is correct after a refresh / on other
     * devices). Synthetic bridge items (ann_/fee_) are not server rows, so the
     * call only affects real NotificationsTable rows — which is correct.
     */
    fun markAllRead() {
        _state.update { s ->
            s.copy(
                notifications = s.notifications.map { it.copy(unread = false) },
                unreadCount = 0,
            )
        }
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first() ?: return@launch
            repository.markAllNotificationsRead(token)
        }
    }

    /** RA-46: persist a single mark-read on the server (optimistic UI first). */
    fun markRead(id: String) {
        _state.update { s ->
            val updated = s.notifications.map { if (it.id == id) it.copy(unread = false) else it }
            s.copy(notifications = updated, unreadCount = updated.count { it.unread })
        }
        // Only real rows (UUID ids) are server-persistable; synth bridge ids are
        // prefixed (ann_/fee_) and are skipped server-side anyway.
        if (!id.startsWith("ann_") && !id.startsWith("fee_")) {
            viewModelScope.launch {
                val token = preferenceRepository.getUserToken().first() ?: return@launch
                repository.markNotificationRead(token, id)
            }
        }
    }
}
