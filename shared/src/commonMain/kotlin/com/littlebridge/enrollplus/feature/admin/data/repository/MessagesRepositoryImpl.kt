/*
 * File: MessagesRepositoryImpl.kt
 * Module: feature.admin.data.repository
 *
 * Default [MessagesRepository] implementation. Standard ApiResponse envelope
 * unwrapping, identical pattern to OnboardingRepositoryImpl /
 * AdmissionRepositoryImpl.
 */
package com.littlebridge.enrollplus.feature.admin.data.repository

import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.data.remote.MessagesApi
import com.littlebridge.enrollplus.feature.admin.domain.model.MessageThread
import com.littlebridge.enrollplus.feature.admin.domain.model.SendMessageRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.SendMessageResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.ThreadMessagesResponse
import com.littlebridge.enrollplus.feature.admin.domain.repository.MessagesRepository

class MessagesRepositoryImpl(
    private val api: MessagesApi
) : MessagesRepository {

    override suspend fun getThreads(token: String): NetworkResult<List<MessageThread>> {
        return when (val result = api.getThreads(token)) {
            is NetworkResult.Success -> {
                val envelope = result.data
                val data = envelope.data
                when {
                    !envelope.success -> NetworkResult.Error(
                        envelope.message.ifBlank { "Failed to fetch message threads" }
                    )
                    data == null -> NetworkResult.Error("No data in response")
                    else -> NetworkResult.Success(data.threads)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun getThreadMessages(
        token: String,
        threadId: String
    ): NetworkResult<ThreadMessagesResponse> {
        return when (val result = api.getThreadMessages(token, threadId)) {
            is NetworkResult.Success -> {
                val envelope = result.data
                val data = envelope.data
                when {
                    !envelope.success -> NetworkResult.Error(
                        envelope.message.ifBlank { "Failed to fetch conversation" }
                    )
                    data == null -> NetworkResult.Error("No data in response")
                    else -> NetworkResult.Success(data)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun markThreadRead(
        token: String,
        threadId: String
    ): NetworkResult<Unit> {
        return when (val result = api.markThreadRead(token, threadId)) {
            is NetworkResult.Success -> {
                val envelope = result.data
                if (!envelope.success) {
                    NetworkResult.Error(
                        envelope.message.ifBlank { "Failed to mark thread as read" }
                    )
                } else {
                    NetworkResult.Success(Unit)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun sendMessage(
        token: String,
        request: SendMessageRequest
    ): NetworkResult<SendMessageResponse> {
        return when (val result = api.sendMessage(token, request)) {
            is NetworkResult.Success -> {
                val envelope = result.data
                val data = envelope.data
                when {
                    !envelope.success -> NetworkResult.Error(
                        envelope.message.ifBlank { "Failed to send message" }
                    )
                    data == null -> NetworkResult.Error("No data in response")
                    else -> NetworkResult.Success(data)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }
}
