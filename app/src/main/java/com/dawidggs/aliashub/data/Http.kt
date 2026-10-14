package com.dawidggs.aliashub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

internal object Http {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    val EMPTY_BODY: RequestBody = "".toRequestBody(null)

    fun jsonBody(obj: JSONObject): RequestBody = obj.toString().toRequestBody(JSON_TYPE)

    /** Custom base URL (self-hosted) if given, else [default]. No trailing slash. */
    fun baseUrl(custom: String?, default: String): String {
        val c = custom?.trim().orEmpty()
        return if (c.isEmpty()) default else c.trimEnd('/')
    }

    /** API keys must never travel over plain http, so self-hosted URLs have to be https. */
    fun isValidHttpsBase(url: String): Boolean = url.toHttpUrlOrNull()?.isHttps == true

    /** Runs the request off the main thread and returns the body, or throws [ProviderException]. */
    suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) {
                    throw errorFor(resp.code, text, resp.header("Retry-After"))
                }
                text
            }
        } catch (e: IOException) {
            throw ProviderException(ProviderException.Kind.NETWORK, e.message ?: "network error")
        }
    }

    /** Turns malformed or unexpected JSON into a clear "API changed" error instead of a crash. */
    inline fun <T> parsing(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw ProviderException(ProviderException.Kind.BAD_RESPONSE, "unexpected response shape")
    }

    private fun errorFor(code: Int, body: String, retryAfter: String?): ProviderException {
        val detail = extractMessage(body)
        return when (code) {
            401 -> ProviderException(
                ProviderException.Kind.AUTH,
                detail ?: "invalid or revoked API key",
                code
            )
            403 -> ProviderException(
                ProviderException.Kind.FORBIDDEN,
                detail ?: "not allowed (plan limit or missing permission)",
                code,
            )
            404 -> ProviderException(ProviderException.Kind.NOT_FOUND, detail ?: "not found", code)
            429 -> ProviderException(
                ProviderException.Kind.RATE_LIMIT,
                "rate limited" + (retryAfter?.let { " (retry after ${it}s)" } ?: ""),
                code,
            )
            in 500..599 -> ProviderException(
                ProviderException.Kind.SERVER,
                detail ?: "server error $code",
                code
            )
            else -> ProviderException(ProviderException.Kind.OTHER, detail ?: "HTTP $code", code)
        }
    }

    /** SimpleLogin uses {"error"}, Addy {"message"}, Relay (Django REST) {"detail"}. */
    private fun extractMessage(body: String): String? = try {
        val o = JSONObject(body)
        o.str("error").ifBlank { o.str("message") }.ifBlank { o.str("detail") }.ifBlank { null }
    } catch (e: JSONException) {
        null
    }
}

/** org.json turns JSON null into the string "null"; this returns "" instead. */
internal fun JSONObject.str(name: String): String = if (isNull(name)) "" else optString(name, "")

internal fun JSONObject.int(name: String): Int = optInt(name, 0)

/** Accepts "2024-05-01T10:00:00Z", offsets, and Addy's "2024-05-01 10:00:00" (UTC). */
internal fun parseMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(raw.trim()).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            LocalDateTime.parse(raw.trim().replace(' ', 'T')).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (e2: Exception) {
            null
        }
    }
}
