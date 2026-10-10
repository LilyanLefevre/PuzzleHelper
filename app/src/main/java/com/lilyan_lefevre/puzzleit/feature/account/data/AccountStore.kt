package com.lilyan_lefevre.puzzleit.feature.account.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The signed-in person on a server, as the screen needs it. */
data class Account(val server: String, val email: String)

/**
 * Where the server is and who is signed in, kept on the device (the password never is, only the server's token), plus the list of
 * things deleted here that the server must still be told about.
 */
@Singleton
class AccountStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<Account?> = _account.asStateFlow()

    val lastServer: String get() = prefs.getString("server", "").orEmpty()

    val token: String? get() = prefs.getString("token", null)

    private fun readAccount(): Account? {
        val server = prefs.getString("server", null) ?: return null
        val email = prefs.getString("email", null) ?: return null
        return if (prefs.getString("token", null) != null) Account(server, email) else null
    }

    fun signIn(server: String, session: Session) {
        prefs.edit().putString("server", server.trim().trimEnd('/')).putString("token", session.token).putString("email", session.email).apply()
        _account.value = readAccount()
    }

    fun updateToken(session: Session) {
        prefs.edit().putString("token", session.token).apply()
    }

    /** Keeps the server address typed (so it is still there next time) but forgets the person. */
    fun signOut() {
        prefs.edit().remove("token").remove("email").apply()
        _account.value = null
    }

    /**
     * Keys of what was deleted on this device ("puzzle:<id>", "scan:<puzzle>:<date>", "photo:<puzzle>:<date>"): without them the next sync would
     * download the thing again. Only kept while signed in, since without an account there is nothing to tell the server.
     */
    val pendingDeletions: Set<String> get() = prefs.getStringSet("deleted", emptySet()).orEmpty()

    fun markDeleted(key: String) {
        if (_account.value == null) return
        prefs.edit().putStringSet("deleted", pendingDeletions + key).apply()
    }

    fun deletionDone(key: String) {
        prefs.edit().putStringSet("deleted", pendingDeletions - key).apply()
    }
}
