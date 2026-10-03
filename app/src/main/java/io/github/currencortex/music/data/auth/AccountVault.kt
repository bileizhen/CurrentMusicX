package io.github.currencortex.music.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.security.SecureTokenStore
import io.github.currencortex.music.core.security.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.security.MessageDigest

/** Public metadata only. Credentials reside in separate Keystore-encrypted files. */
@Serializable data class SavedAccount(val key: String, val id: Long, val server: String,
    val username: String = "", val nickname: String = "", val avatar: String = "") {
    fun account() = Account(id, nickname.ifBlank { username }, username, avatar)
}
interface AccountVault {
    val accounts: StateFlow<List<SavedAccount>>
    suspend fun current(): SavedAccount?
    suspend fun remember(server: String, user: UserDto, token: String)
    suspend fun credential(key: String): Pair<SavedAccount, String>?
    suspend fun select(key: String)
    suspend fun detach(forget: Boolean)
    suspend fun remove(key: String)
}
class EncryptedAccountVault(private val preferences: DataStore<Preferences>, scope: CoroutineScope,
    private val tokenStore: (String) -> TokenStore) : AccountVault {
    private val entriesKey = stringPreferencesKey("accounts.metadata")
    private val activeKey = stringPreferencesKey("accounts.active")
    private fun entries(p: Preferences): List<SavedAccount> = p[entriesKey]?.let {
        ApiJson.decodeFromString<List<SavedAccount>>(it)
    } ?: emptyList()
    override val accounts = preferences.data.map(::entries).stateIn(scope, SharingStarted.Eagerly, emptyList())
    override suspend fun current(): SavedAccount? = preferences.data.first().let { p -> entries(p).find { it.key == p[activeKey] } }
    override suspend fun remember(server: String, user: UserDto, token: String) {
        val key = accountKey(server, user.id)
        withContext(Dispatchers.IO) { tokenStore(key).write(token) }
        val entry = SavedAccount(key, user.id, server, user.username, user.nickname, user.avatar)
        preferences.edit { p -> p[entriesKey] = ApiJson.encodeToString(listOf(entry) + entries(p).filterNot { it.key == key }); p[activeKey] = key }
    }
    override suspend fun credential(key: String): Pair<SavedAccount, String>? {
        val entry = entries(preferences.data.first()).find { it.key == key } ?: return null
        return withContext(Dispatchers.IO) { tokenStore(entry.key).read()?.let { entry to it } }
    }
    override suspend fun select(key: String) { preferences.edit { it[activeKey] = key } }
    override suspend fun detach(forget: Boolean) {
        val key = preferences.data.first()[activeKey]
        if (forget && key != null) remove(key) else preferences.edit { it.remove(activeKey) }
    }
    override suspend fun remove(key: String) {
        val exists = entries(preferences.data.first()).any { it.key == key }
        if (!exists) return
        withContext(Dispatchers.IO) { tokenStore(key).clear() }
        preferences.edit { p -> p[entriesKey] = ApiJson.encodeToString(entries(p).filterNot { it.key == key }); if (p[activeKey] == key) p.remove(activeKey) }
    }
    companion object {
        fun accountKey(server: String, id: Long) = MessageDigest.getInstance("SHA-256")
            .digest("$server\n$id".toByteArray()).joinToString("") { "%02x".format(it) }
        fun create(context: Context, suffix: String, preferences: DataStore<Preferences>, scope: CoroutineScope) =
            EncryptedAccountVault(preferences, scope) { key -> SecureTokenStore(context, ".account$suffix.$key") }
    }
}
