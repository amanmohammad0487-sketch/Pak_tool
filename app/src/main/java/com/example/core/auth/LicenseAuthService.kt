package com.example.core.auth

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface AuthResult {
    data class Success(
        val key: String,
        val expiry: String,
        val deviceId: String,
        val deviceName: String,
        val message: String
    ) : AuthResult

    data class Denied(val reason: String) : AuthResult
}

/**
 * Exact Kotlin port of the GOAT Key Panel authentication flow
 * against https://goatkeypanel.pythonanywhere.com/.
 */
class LicenseAuthService(private val context: Context) {

    companion object {
        private const val AUTH_BASE_URL = "https://goatkeypanel.pythonanywhere.com"
        private val AUTH_ENDPOINTS = listOf(
            "$AUTH_BASE_URL/api/verify",
            "$AUTH_BASE_URL/verify",
            "$AUTH_BASE_URL/api/validate"
        )
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getDeviceId(): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            if (!androidId.isNullOrBlank()) {
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest("MAJLISH:$androidId".toByteArray(Charsets.UTF_8))
                val hex = digest.take(6).joinToString("") { "%02X".format(it) }
                "DEV-$hex"
            } else {
                "DEV-${Build.BOARD.hashCode().toUInt().toString(16).uppercase()}"
            }
        } catch (_: Exception) {
            "DEV-UNKNOWN-123"
        }
    }

    fun getDeviceName(): String {
        val model = Build.MODEL?.trim().orEmpty()
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        return when {
            model.isBlank() -> "Android Device"
            manufacturer.isNotBlank() && !model.startsWith(manufacturer, ignoreCase = true) ->
                "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
            else -> model
        }
    }

    suspend fun verifyKey(rawKey: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanedKey = rawKey.trim().trim('`', '"', '\'')
        if (cleanedKey.isEmpty()) {
            return@withContext AuthResult.Denied("NO KEY ENTERED")
        }

        val deviceId = getDeviceId()
        val deviceName = getDeviceName()

        var lastError: String? = null

        for (endpoint in AUTH_ENDPOINTS) {
            for (attempt in 0 until 2) {
                try {
                    val (statusCode, json) = executeSingleRequest(
                        endpoint = endpoint,
                        key = cleanedKey,
                        deviceId = deviceId,
                        deviceName = deviceName
                    )

                    if (statusCode == 404 || statusCode == 405) {
                        val hasBodyMessage = json.optString("message").isNotBlank() ||
                            json.optString("status").isNotBlank() ||
                            json.optString("error").isNotBlank()
                        if (hasBodyMessage) {
                            return@withContext AuthResult.Denied(
                                extractReason(json, "ACCESS DENIED")
                            )
                        }
                        // Wrong route -> try next endpoint
                        break
                    }

                    if (statusCode >= 500) {
                        lastError = "HTTP $statusCode"
                        delay(1000)
                        continue
                    }

                    if (statusCode == 200 && isSuccess(json)) {
                        val expiry = listOf("expires_at", "expiry", "expires")
                            .firstNotNullOfOrNull { field ->
                                json.optString(field).takeIf { it.isNotBlank() && it != "null" }
                            } ?: "N/A"
                        val msg = json.optString("message").ifBlank { "ACCESS GRANTED" }
                        return@withContext AuthResult.Success(
                            key = cleanedKey,
                            expiry = expiry,
                            deviceId = deviceId,
                            deviceName = deviceName,
                            message = msg
                        )
                    }

                    val defaultReason = if (statusCode == 200) "INVALID KEY" else "ACCESS DENIED"
                    return@withContext AuthResult.Denied(extractReason(json, defaultReason))
                } catch (_: SocketTimeoutException) {
                    lastError = "REQUEST TIMEOUT"
                    delay(1000)
                } catch (e: IOException) {
                    lastError = e.message?.take(120) ?: "NETWORK ERROR"
                    delay(1000)
                } catch (e: Exception) {
                    lastError = e.message?.take(120) ?: "AUTH ERROR"
                    break
                }
            }
        }

        AuthResult.Denied((lastError ?: "AUTH ENDPOINT NOT FOUND").uppercase())
    }

    private fun executeSingleRequest(
        endpoint: String,
        key: String,
        deviceId: String,
        deviceName: String
    ): Pair<Int, JSONObject> {
        val getUrl = endpoint.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("key", key)
            ?.addQueryParameter("device_id", deviceId)
            ?.addQueryParameter("hwid", deviceId)
            ?.addQueryParameter("device_name", deviceName)
            ?.build()
            ?: throw IOException("Invalid auth URL")

        val getReq = Request.Builder()
            .url(getUrl)
            .get()
            .header("User-Agent", "MAJLISH-Android/3.0")
            .build()

        client.newCall(getReq).execute().use { resp ->
            val code = resp.code
            val body = resp.body?.string().orEmpty()
            val json = parseJson(body)

            if (code != 404 && code != 405) {
                return code to json
            }
        }

        val payload = JSONObject().apply {
            put("key", key)
            put("device_id", deviceId)
            put("hwid", deviceId)
            put("device_name", deviceName)
        }

        val postReq = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", "MAJLISH-Android/3.0")
            .build()

        client.newCall(postReq).execute().use { resp ->
            val code = resp.code
            val body = resp.body?.string().orEmpty()
            return code to parseJson(body)
        }
    }

    private fun parseJson(raw: String): JSONObject {
        return try {
            if (raw.trim().startsWith("{")) JSONObject(raw) else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun isSuccess(data: JSONObject): Boolean {
        val status = data.optString("status", "").trim().lowercase()
        if (status in setOf("success", "ok", "valid", "active", "authorized")) {
            return true
        }
        if (data.optBoolean("success", false) || data.optBoolean("valid", false)) {
            return true
        }
        return false
    }

    private fun extractReason(data: JSONObject, default: String): String {
        for (field in listOf("message", "error", "reason")) {
            val v = data.optString(field, "").trim()
            if (v.isNotEmpty() && v != "null") {
                return v.uppercase()
            }
        }
        return default
    }
}
