package com.lilyan_lefevre.puzzleit.feature.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lilyan_lefevre.puzzleit.feature.account.data.BackendException
import com.lilyan_lefevre.puzzleit.feature.account.data.PocketBaseClient
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "sign in with Google" flow against a real PocketBase with a fake Google provider configured (opt-in: POCKETBASE_OAUTH_URL, see server/README.md).
 * The browser is simulated by calling the redirect address ourselves; PocketBase then pushes the code over /api/realtime and tries to trade it with
 * Google, which refuses the fake one: reaching that refusal proves the whole chain up to the provider works.
 */
@RunWith(AndroidJUnit4::class)
class OAuthIntegrationTest {

    private val server = System.getenv("POCKETBASE_OAUTH_URL")
    private val http = OkHttpClient()
    private val client = PocketBaseClient(http)

    @Test
    fun `the provider list comes from the server and the redirect code reaches the app`() = runBlocking {
        assumeTrue("POCKETBASE_OAUTH_URL not set", server != null)
        val providers = client.providers(server!!)
        assertEquals(listOf("google"), providers.map { it.name })
        var opened = ""
        try {
            client.signInWithProvider(server, providers.first()) { url ->
                opened = url
                // What the browser does once the person agreed: Google sends it to the server's redirect address.
                val state = url.toHttpUrl().queryParameter("state")
                Thread { http.newCall(Request.Builder().url("$server/api/oauth2-redirect?state=$state&code=fake-code").build()).execute().close() }.start()
            }
            fail("a fake code must not give a session")
        } catch (e: BackendException) {
            assertEquals("the server tried to trade the code (${e.message})", 400, e.code)
        }
        assertTrue(opened, opened.startsWith("https://accounts.google.com/") && opened.contains("redirect_uri=" + java.net.URLEncoder.encode("$server/api/oauth2-redirect", "UTF-8")))
    }
}
