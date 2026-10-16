package com.example.voiceassistant.data

import android.content.Context
import com.example.voiceassistant.BuildConfig
import com.example.voiceassistant.domain.model.ChatMessage
import com.example.voiceassistant.domain.model.MessageSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Calls a Supabase Edge Function which holds the actual AI provider key server-side.
 * The Supabase anon key is a public project key; the OpenAI secret must never be placed in the APK.
 */
class AiChatClient(context: Context) {
    @Suppress("unused")
    private val appContext = context.applicationContext

    private val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") && anonKey.isNotBlank()

    @Volatile
    private var cachedAccessToken: String? = null


    /**
     * Normalize an unfamiliar phrase into an existing local allowlisted command.
     * Callers must re-parse the result locally before any action can run.
     */
    suspend fun interpretCommand(command: String): String = withContext(Dispatchers.IO) {
        if (!isConfigured) throw IOException("AI command understanding is not configured.")
        val token = cachedAccessToken ?: createAnonymousSession().also { cachedAccessToken = it }
        try {
            invokeIntent(token, command)
        } catch (error: IOException) {
            if (!error.message.orEmpty().contains("HTTP 401") &&
                !error.message.orEmpty().contains("HTTP 403")) throw error
            val refreshed = createAnonymousSession()
            cachedAccessToken = refreshed
            invokeIntent(refreshed, command)
        }
    }

    private fun invokeIntent(accessToken: String, command: String): String {
        val connection = openConnection("$supabaseUrl/functions/v1/assistant-chat", "POST")
        try {
            connection.setRequestProperty("apikey", anonKey)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use {
                it.write(JSONObject().put("mode", "interpret")
                    .put("command", command.take(MAX_COMMAND_CHARS))
                    .toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val body = readBody(connection, status)
            if (status !in 200..299) throw IOException("AI command interpretation failed (HTTP $status).")
            return JSONObject(body).optString("normalized_command").trim().take(MAX_COMMAND_CHARS)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun reply(history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            throw IOException("AI is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY to VA/local.properties.")
        }
        val messages = JSONArray()
        history.takeLast(MAX_HISTORY).forEach { message ->
            if (message.sender == MessageSender.USER || message.sender == MessageSender.ASSISTANT) {
                val role = if (message.sender == MessageSender.USER) "user" else "assistant"
                val text = message.text.take(MAX_MESSAGE_CHARS)
                if (text.isNotBlank()) {
                    messages.put(JSONObject().put("role", role).put("content", text))
                }
            }
        }
        if (messages.length() == 0) throw IOException("There is no conversation to send.")

        val token = cachedAccessToken ?: createAnonymousSession().also { cachedAccessToken = it }
        try {
            invokeAssistant(token, messages)
        } catch (error: IOException) {
            // A stale JWT can expire. Re-authenticate once and retry only authorization failures.
            if (!error.message.orEmpty().contains("HTTP 401") &&
                !error.message.orEmpty().contains("HTTP 403")) throw error
            val refreshed = createAnonymousSession()
            cachedAccessToken = refreshed
            invokeAssistant(refreshed, messages)
        }
    }

    private fun createAnonymousSession(): String {
        val connection = openConnection("$supabaseUrl/auth/v1/signup", "POST")
        try {
            connection.setRequestProperty("apikey", anonKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(JSONObject().put("data", JSONObject()).toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val body = readBody(connection, status)
            if (status !in 200..299) throw IOException("Supabase anonymous sign-in failed (HTTP $status). Check anonymous sign-in is enabled.")
            val token = JSONObject(body).optString("access_token")
            if (token.isBlank()) throw IOException("Supabase did not return a session token. Enable anonymous sign-in in Supabase Auth.")
            return token
        } finally {
            connection.disconnect()
        }
    }

    private fun invokeAssistant(accessToken: String, messages: JSONArray): String {
        val connection = openConnection("$supabaseUrl/functions/v1/assistant-chat", "POST")
        try {
            connection.setRequestProperty("apikey", anonKey)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use {
                it.write(JSONObject().put("messages", messages).toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val body = readBody(connection, status)
            if (status !in 200..299) {
                val safeDetail = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                throw IOException("AI request failed (HTTP $status)${if (safeDetail.isBlank()) "" else ": $safeDetail"}")
            }
            val reply = JSONObject(body).optString("reply").trim()
            if (reply.isBlank()) throw IOException("AI service returned an empty reply.")
            return reply
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(address: String, method: String): HttpURLConnection {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            doInput = true
        }
        return connection
    }

    private fun readBody(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(MAX_RESPONSE_CHARS) }.orEmpty()
    }

    private companion object {
        const val MAX_COMMAND_CHARS = 500
        const val MAX_HISTORY = 20
        const val MAX_MESSAGE_CHARS = 3000
        const val MAX_RESPONSE_CHARS = 16000
        const val CONNECT_TIMEOUT_MS = 15000
        const val READ_TIMEOUT_MS = 45000
    }
}
