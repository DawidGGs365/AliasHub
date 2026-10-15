package com.dawidggs.aliashub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    /**
     * Sends the request and returns the body, or throws [ProviderException]. Cancelling the
     * calling coroutine cancels the in-flight HTTP call too.
     */
    suspend fun execute(request: Request): String =
        try {
            client.newCall(request).await().use { resp ->
                // Reading the body is blocking I/O, keep it off the caller's thread.
                val text = withContext(Dispatchers.IO) { resp.body.string() }
                if (!resp.isSuccessful) {
                    throw ProviderException(
                        kindFor(resp.code),
                        extractMessage(text),
                        resp.code,
                        resp.header("Retry-After"),
                    )
                }
                text
            }
        } catch (e: IOException) {
            throw ProviderException(ProviderException.Kind.NETWORK, e.message ?: "network error")
        }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    /** Turns malformed or unexpected JSON into a clear "API changed" error instead of a crash. */
    inline fun <T> parsing(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw ProviderException(ProviderException.Kind.BAD_RESPONSE, "unexpected response shape")
    }

    private fun kindFor(code: Int): ProviderException.Kind = when (code) {
        401 -> ProviderException.Kind.AUTH
        403 -> ProviderException.Kind.FORBIDDEN
        404 -> ProviderException.Kind.NOT_FOUND
        429 -> ProviderException.Kind.RATE_LIMIT
        in 500..599 -> ProviderException.Kind.SERVER
        else -> ProviderException.Kind.OTHER
    }

    /** SimpleLogin uses {"error"}, Addy {"message"}, Relay (Django REST) {"detail"}. */
    private fun extractMessage(body: String): String? = try {
        val o = JSONObject(body)
        o.str("error").ifBlank { o.str("message") }.ifBlank { o.str("detail") }.ifBlank { null }
    } catch (e: JSONException) {
        null
    }
}

/** `request(path).send()` / `.send("POST", body)`: build and run in one step. */
internal suspend fun Request.Builder.send(verb: String = "GET", body: RequestBody? = null): String =
    Http.execute(method(verb, body).build())

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
