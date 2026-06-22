package com.littlebridge.enrollplus.core.network

import com.littlebridge.enrollplus.util.AppLogger
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.errors.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val code: Int? = null) : NetworkResult<Nothing>()
    data object ConnectionError : NetworkResult<Nothing>()
}

/**
 * Minimal projection of the canonical `{success, message, data}` envelope used
 * only to extract a human-readable error message (RA-30). Lenient + ignores
 * unknown keys so it survives schema drift and extra fields.
 */
@Serializable
private data class ErrorEnvelope(val message: String? = null, val error: String? = null)

private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * RA-30: robustly extract a server error message from a non-2xx response body.
 * Replaces fragile `substringAfter("\"message\":\"")` parsing (which broke on
 * escaped quotes, pretty-printed JSON, or a null message) with proper lenient
 * deserialization of the standard envelope, falling back to the status line.
 */
fun extractErrorMessage(errorBody: String, statusCode: Int): String {
    val trimmed = errorBody.trim()
    if (trimmed.startsWith("{")) {
        val parsed = runCatching {
            errorJson.decodeFromString(ErrorEnvelope.serializer(), trimmed)
        }.getOrNull()
        val msg = parsed?.message?.takeIf { it.isNotBlank() }
            ?: parsed?.error?.takeIf { it.isNotBlank() }
        if (msg != null) return msg
    }
    return trimmed.ifBlank { "Server returned $statusCode" }
}

fun redactedHeaders(headers: Headers): List<Pair<String, List<String>>> {
    val sensitive = setOf(
        HttpHeaders.Authorization.lowercase(),
        HttpHeaders.Cookie.lowercase(),
        HttpHeaders.SetCookie.lowercase(),
        "x-api-key"
    )
    return headers.entries().map { (name, values) ->
        if (name.lowercase() in sensitive) name to listOf("[REDACTED]") else name to values
    }
}

fun redactedBodyText(body: String): String {
    if (body.isBlank()) return body

    val sensitiveKeys = listOf(
        "password",
        "otp",
        "code",
        "token",
        "accessToken",
        "refreshToken",
        "refresh_token",
        "authorization",
        "cookie",
        "apiKey",
        "api_key"
    )

    var sanitized = body
    sensitiveKeys.forEach { key ->
        sanitized = sanitized
            .replace(Regex("(?i)(\\\"$key\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")"), "$1[REDACTED]$2")
            .replace(Regex("(?i)(\\b$key\\s*=\\s*)[^,)]*"), "$1[REDACTED]")
    }
    return sanitized
}

suspend inline fun <reified T> safeApiCall(block: () -> HttpResponse): NetworkResult<T> {
    return try {
        val response = block()
        val request = response.call.request
        
        // Log Request. Never print raw Authorization headers; Android Studio logs
        // are frequently shared while debugging and bearer tokens must not leak.
        AppLogger.d("API_CALL", "--- START API CALL ---")
        AppLogger.d("API_CALL", "REQUEST: ${request.method.value} ${request.url}")
        //AppLogger.d("API_CALL", "REQUEST HEADERS: ${redactedHeaders(request.headers)}")
        AppLogger.d("API_CALL", "REQUEST HEADERS: ${request.headers}")

        val requestBody = request.content
        if (requestBody is TextContent) {
            AppLogger.d("API_CALL", "REQUEST BODY: ${redactedBodyText(requestBody.text)}")
        } else if (requestBody !is OutgoingContent.NoContent) {
            AppLogger.d("API_CALL", "REQUEST BODY: [Binary/Serialized Content]")
        }

        if (response.status.value in 200..299) {
            val body = response.body<T>()
            AppLogger.d("API_CALL", "RESPONSE STATUS: ${response.status.value}")
            AppLogger.d("API_CALL", "RESPONSE BODY: ${redactedBodyText(body.toString())}")
            AppLogger.d("API_CALL", "--- END API CALL (SUCCESS) ---")
            NetworkResult.Success(body)
        } else {
            val errorBody = response.bodyAsText()
            AppLogger.e("API_CALL", "RESPONSE STATUS: ${response.status.value}")
            AppLogger.e("API_CALL", "RESPONSE ERROR BODY: ${redactedBodyText(errorBody)}")
            
            val message = extractErrorMessage(errorBody, response.status.value)
            AppLogger.d("API_CALL", "--- END API CALL (ERROR) ---")
            NetworkResult.Error(message, response.status.value)
        }
    } catch (e: ResponseException) {
        // This handles cases where Ktor plugins (like validator) throw on non-2xx
        AppLogger.e("API_CALL", "HTTP ERROR: ${e.message}")
        NetworkResult.Error(e.message ?: "Server Error", e.response.status.value)
    } catch (e: IOException) {
        AppLogger.e("API_CALL", "CONNECTION ERROR: ${e.message}")
        NetworkResult.ConnectionError
    } catch (e: SerializationException) {
        AppLogger.e("API_CALL", "PARSING ERROR: ${e.message}")
        NetworkResult.Error("Data parsing error")
    } catch (e: Exception) {
        AppLogger.e("API_CALL", "UNKNOWN ERROR: ${e.message}")
        NetworkResult.Error(e.message ?: "An unknown error occurred")
    }
}
