package com.lilyan_lefevre.puzzleit.feature.account

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.feature.account.data.Account
import com.lilyan_lefevre.puzzleit.feature.account.data.AccountStore
import com.lilyan_lefevre.puzzleit.feature.account.data.BackendException
import com.lilyan_lefevre.puzzleit.feature.account.data.PocketBaseClient
import com.lilyan_lefevre.puzzleit.feature.account.data.SyncReport
import com.lilyan_lefevre.puzzleit.feature.account.data.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the account screen tells the person after an action. */
sealed interface Notice {
    data class Synced(val uploaded: Int, val downloaded: Int) : Notice
    /** [detail] is the server's own message or the network error, [reason] a string resource when the cause is on our side. */
    data class Failed(@StringRes val reason: Int?, val detail: String = "") : Notice
}

/** Signing in to the person's own server (PocketBase) and syncing the puzzles with it. */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val client: PocketBaseClient,
    private val store: AccountStore,
    private val sync: SyncRepository,
) : ViewModel() {

    val account: StateFlow<Account?> = store.account
    val lastServer: String get() = store.lastServer

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    fun signIn(server: String, email: String, password: String, create: Boolean) {
        val url = server.trim()
        when {
            !url.startsWith("http://") && !url.startsWith("https://") -> { _notice.value = Notice.Failed(R.string.server_invalid); return }
            email.isBlank() || password.length < 8 -> { _notice.value = Notice.Failed(R.string.credentials_invalid); return }
        }
        run {
            val session = if (create) client.register(url, email.trim(), password) else client.signIn(url, email.trim(), password)
            store.signIn(url, session)
            sync.sync()
        }
    }

    fun syncNow() = run { sync.sync() }

    fun signOut() = store.signOut()

    private fun run(block: suspend () -> SyncReport) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _notice.value = try {
                block().let { Notice.Synced(it.uploaded, it.downloaded) }
            } catch (e: BackendException) {
                Notice.Failed(null, e.message.orEmpty())
            } finally {
                _busy.value = false
            }
        }
    }
}
