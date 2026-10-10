package com.lilyan_lefevre.puzzleit.feature.account.data

import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject

/** The server answered with an error ([code] is the HTTP status, 0 when it could not be reached) or something unexpected. */
class BackendException(message: String, val code: Int = 0) : IOException(message)

/** Who is signed in: the token the server gave, and the person's id there. */
data class Session(val token: String, val userId: String, val email: String)

/** A way to sign in that the server offers (Google, GitHub...). [authUrl] is the provider's page, still missing the redirect address. */
data class OAuthProvider(val name: String, val displayName: String, val authUrl: String, val codeVerifier: String)

/**
 * A small client for a PocketBase server (https://pocketbase.io), the backend the owner hosts on their own machine.
 * Only what the sync needs: sign in, list / create / update / delete records, download protected files.
 * The server is passed to every call so the person can point the app at any PocketBase (home Raspberry, VPS...).
 */
@Singleton
class PocketBaseClient @Inject constructor(private val http: OkHttpClient) {

    private fun base(server: String) = server.trim().trimEnd('/')

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(body).getString("message") }.getOrDefault(response.message)
                    throw BackendException(message, response.code)
                }
                body
            }
        } catch (e: BackendException) {
            throw e
        } catch (e: IOException) {
            throw BackendException(e.message ?: "unreachable")
        } catch (e: IllegalArgumentException) {
            throw BackendException("invalid server address")
        }
    }

    private fun session(json: JSONObject) =
        Session(json.getString("token"), json.getJSONObject("record").getString("id"), json.getJSONObject("record").optString("email"))

    private fun jsonBody(vararg pairs: Pair<String, String>): RequestBody =
        JSONObject(mapOf(*pairs)).toString().toRequestBody("application/json".toMediaType())

    suspend fun signIn(server: String, email: String, password: String): Session =
        session(JSONObject(call(Request.Builder().url("${base(server)}/api/collections/users/auth-with-password")
            .post(jsonBody("identity" to email, "password" to password)).build())))

    /** Creates the account, then signs in. */
    suspend fun register(server: String, email: String, password: String): Session {
        call(Request.Builder().url("${base(server)}/api/collections/users/records")
            .post(jsonBody("email" to email, "password" to password, "passwordConfirm" to password)).build())
        return signIn(server, email, password)
    }

    /** The "sign in with ..." providers the server has switched on: none until they are set up in the PocketBase dashboard. */
    suspend fun providers(server: String): List<OAuthProvider> {
        val list = JSONObject(call(Request.Builder().url("${base(server)}/api/collections/users/auth-methods").build()))
            .optJSONObject("oauth2")?.optJSONArray("providers") ?: return emptyList()
        return List(list.length()) { list.getJSONObject(it) }.map {
            OAuthProvider(it.getString("name"), it.optString("displayName").ifEmpty { it.getString("name") }, it.getString("authURL"), it.getString("codeVerifier"))
        }
    }

    /**
     * PocketBase's own OAuth2 flow, the one its JS SDK uses: listen on /api/realtime, have [openBrowser] show the provider's page, wait for the server
     * to push the code once the person agreed, then trade it for a session. Gives up after three minutes.
     */
    suspend fun signInWithProvider(server: String, provider: OAuthProvider, openBrowser: (String) -> Unit): Session {
        val root = base(server)
        val redirect = "$root/api/oauth2-redirect"
        val events = Channel<Pair<String, String>>(Channel.UNLIMITED)
        val source = EventSources.createFactory(http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build())
            .newEventSource(Request.Builder().url("$root/api/realtime").build(), object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    events.trySend((type ?: "") to data)
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    events.close(BackendException(t?.message ?: response?.message ?: "unreachable", response?.code ?: 0))
                }

                override fun onClosed(eventSource: EventSource) {
                    events.close()
                }
            })
        try {
            return withTimeout(180_000) {
                val clientId = JSONObject(events.next("PB_CONNECT")).getString("clientId")
                call(Request.Builder().url("$root/api/realtime")
                    .post(JSONObject().put("clientId", clientId).put("subscriptions", JSONArray().put("@oauth2")).toString().toRequestBody("application/json".toMediaType())).build())
                // The state is the realtime client's id: it is how the server finds the connection to push the code to (the JS SDK does the same).
                openBrowser((provider.authUrl + URLEncoder.encode(redirect, "UTF-8")).toHttpUrl().newBuilder().setQueryParameter("state", clientId).build().toString())
                val result = JSONObject(events.next("@oauth2"))
                val code = result.optString("code")
                if (result.optString("state") != clientId || code.isEmpty()) throw BackendException(result.optString("error").ifEmpty { "sign-in refused" })
                session(JSONObject(call(Request.Builder().url("$root/api/collections/users/auth-with-oauth2")
                    .post(jsonBody("provider" to provider.name, "code" to code, "codeVerifier" to provider.codeVerifier, "redirectUrl" to redirect)).build())))
            }
        } catch (e: TimeoutCancellationException) {
            throw BackendException("sign-in timed out")
        } finally {
            source.cancel()
            events.cancel()
        }
    }

    private suspend fun Channel<Pair<String, String>>.next(type: String): String {
        for ((t, data) in this) if (t == type) return data
        throw BackendException("connection closed")
    }

    /** A fresh token, so a person who syncs at least every two weeks never has to sign in again. */
    suspend fun refresh(server: String, token: String): Session =
        session(JSONObject(call(Request.Builder().url("${base(server)}/api/collections/users/auth-refresh")
            .header("Authorization", token).post(ByteArray(0).toRequestBody()).build())))

    /** All the records of [collection] matching [filter] (PocketBase filter syntax), page after page. */
    suspend fun list(server: String, token: String, collection: String, filter: String? = null): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var page = 1
        while (true) {
            val url = "${base(server)}/api/collections/$collection/records".toHttpUrl().newBuilder()
                .addQueryParameter("perPage", "200").addQueryParameter("page", page.toString()).apply { if (filter != null) addQueryParameter("filter", filter) }.build()
            val json = JSONObject(call(Request.Builder().url(url).header("Authorization", token).build()))
            val items = json.getJSONArray("items")
            for (i in 0 until items.length()) out += items.getJSONObject(i)
            if (page >= json.getInt("totalPages")) return out
            page++
        }
    }

    private fun multipart(fields: Map<String, String>, files: Map<String, File>): MultipartBody =
        MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (k, v) -> addFormDataPart(k, v) }
            files.forEach { (k, f) -> addFormDataPart(k, f.name, f.asRequestBody("application/octet-stream".toMediaType())) }
        }.build()

    suspend fun create(server: String, token: String, collection: String, fields: Map<String, String>, files: Map<String, File> = emptyMap()): JSONObject =
        JSONObject(call(Request.Builder().url("${base(server)}/api/collections/$collection/records").header("Authorization", token)
            .post(multipart(fields, files)).build()))

    suspend fun update(server: String, token: String, collection: String, id: String, fields: Map<String, String>) {
        call(Request.Builder().url("${base(server)}/api/collections/$collection/records/$id").header("Authorization", token)
            .patch(multipart(fields, emptyMap())).build())
    }

    suspend fun delete(server: String, token: String, collection: String, id: String) {
        call(Request.Builder().url("${base(server)}/api/collections/$collection/records/$id").header("Authorization", token).delete().build())
    }

    /** Downloads a protected file of [record] ([record] is a JSON record whose [field] names the file) into [target]. */
    suspend fun download(server: String, token: String, record: JSONObject, field: String, target: File) {
        val name = record.optString(field)
        if (name.isEmpty()) return
        val fileToken = JSONObject(call(Request.Builder().url("${base(server)}/api/files/token").header("Authorization", token)
            .post(ByteArray(0).toRequestBody()).build())).getString("token")
        val url = "${base(server)}/api/files/${record.getString("collectionId")}/${record.getString("id")}/$name".toHttpUrl()
            .newBuilder().addQueryParameter("token", fileToken).build()
        withContext(Dispatchers.IO) {
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) throw BackendException(r.message, r.code)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
                }
            } catch (e: BackendException) {
                throw e
            } catch (e: IOException) {
                throw BackendException(e.message ?: "unreachable")
            }
        }
    }
}
