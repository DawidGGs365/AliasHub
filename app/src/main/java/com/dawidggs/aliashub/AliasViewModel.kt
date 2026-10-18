package com.dawidggs.aliashub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dawidggs.aliashub.data.AccountInfo
import com.dawidggs.aliashub.data.Alias
import com.dawidggs.aliashub.data.CreateOptions
import com.dawidggs.aliashub.data.CreateRequest
import com.dawidggs.aliashub.data.CredentialStore
import com.dawidggs.aliashub.data.Http
import com.dawidggs.aliashub.data.ProviderConfig
import com.dawidggs.aliashub.data.ProviderException
import com.dawidggs.aliashub.data.ProviderException.Kind
import com.dawidggs.aliashub.data.ProviderType
import com.dawidggs.aliashub.data.Providers
import com.dawidggs.aliashub.data.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import com.dawidggs.aliashub.data.AliasProvider
import java.util.concurrent.ConcurrentHashMap

data class UiState(
    /** False until the credential store has been read (it is opened off the main thread). */
    val initialized: Boolean = false,
    val configured: List<ProviderType> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** True: text uses the device's system font; false: Compose's own default typography. */
    val systemFont: Boolean = true,
    /** Plan and usage per connected provider; missing while loading or if the call failed. */
    val accounts: Map<ProviderType, AccountInfo> = emptyMap(),
    val aliases: List<Alias> = emptyList(),
    val loading: Boolean = false,
    /** One message per provider that failed, so a broken one never hides the others. */
    val errors: Map<ProviderType, String> = emptyMap(),
    /** Keys of aliases with a request in flight. */
    val busy: Set<String> = emptySet(),
    val filter: ProviderType? = null,
    val query: String = "",
) {
    val visible: List<Alias>
        get() = aliases.filter { a ->
            (filter == null || a.provider == filter) &&
                (query.isBlank() ||
                    a.address.contains(query, ignoreCase = true) ||
                    a.note.contains(query, ignoreCase = true))
        }
}

/** A one-off snackbar message; [copyText] adds a "Copy" action. */
data class Message(val text: String, val copyText: String? = null)

class AliasViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context
        get() = getApplication()
    // Opening the encrypted store touches the Android Keystore, which is slow. It is created
    // lazily and first touched from an IO thread in init, so the main thread never pays for it.
    private val store by lazy { CredentialStore(app) }
    private val cache = mutableMapOf<ProviderType, List<Alias>>()

    /** Providers are cheap but decrypting their key is not, so build each one once per config. */
    private val providers = ConcurrentHashMap<ProviderType, AliasProvider>()

    /** In-flight list request per provider; a new refresh replaces (cancels) the old one. */
    private val loads = mutableMapOf<ProviderType, Job>()

    /** Same for the account info shown on the Settings cards. */
    private val accountLoads = mutableMapOf<ProviderType, Job>()

    // Not a secret, so plain preferences. It is tiny and read synchronously so the first frame
    // already has the right theme.
    private val prefs = app.getSharedPreferences("aliashub_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            themeMode = loadThemeMode(),
            systemFont = prefs.getBoolean(KEY_SYSTEM_FONT, true),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = Channel<Message>(Channel.BUFFERED)
    val messages: Flow<Message> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            val configured = withContext(Dispatchers.IO) { store.configuredTypes() }
            _state.update { it.copy(initialized = true, configured = configured) }
            refresh()
        }
    }

    // ---- theme ------------------------------------------------------------------------------

    private fun loadThemeMode(): ThemeMode =
        prefs.getString(KEY_THEME, null)
            ?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } }
            ?: ThemeMode.SYSTEM

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    /** Where "Manage account" goes: the provider's dashboard (own server for self-hosted ones). */
    fun dashboardUrl(type: ProviderType): String =
        Providers.dashboardUrl(type, store.get(type)?.baseUrl)

    fun setSystemFont(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYSTEM_FONT, enabled).apply()
        _state.update { it.copy(systemFont = enabled) }
    }

    // ---- loading ----------------------------------------------------------------------

    /** Reloads every connected provider in parallel, or just [only]. */
    fun refresh(only: ProviderType? = null) {
        val targets = if (only != null) listOf(only) else _state.value.configured
        if (targets.isEmpty()) return
        for (t in targets) {
            loads.remove(t)?.cancel()
            loads[t] = viewModelScope.launch {
                val self = coroutineContext[Job]
                try {
                    val result = attempt { provider(t).list() }
                    // The provider may have been removed while the request was in flight.
                    if (t !in _state.value.configured) return@launch
                    result.onSuccess { cache[t] = it }
                    _state.update { s ->
                        s.copy(
                            errors = result.fold(
                                onSuccess = { s.errors - t },
                                onFailure = { s.errors + (t to describe(t, it)) },
                            ),
                            aliases = merged(),
                        )
                    }
                } finally {
                    if (loads[t] === self) loads.remove(t)
                    _state.update { it.copy(loading = loads.isNotEmpty()) }
                }
            }
        }
        // After the loop: a canceled predecessor may have already cleared the flag.
        _state.update { it.copy(loading = loads.isNotEmpty()) }
    }

    /**
     * Fetches plan and limits for every connected provider (or just [only]). Failures are ignored
     * on purpose: this is extra information, and a real problem with the provider already shows
     * up as the error from [refresh]. Whatever was loaded before stays on screen.
     */
    fun refreshAccounts(only: ProviderType? = null) {
        val targets = if (only != null) listOf(only) else _state.value.configured
        for (t in targets) {
            accountLoads.remove(t)?.cancel()
            accountLoads[t] = viewModelScope.launch {
                val self = coroutineContext[Job]
                try {
                    val result = attempt { provider(t).accountInfo() }
                    if (t !in _state.value.configured) return@launch
                    result.onSuccess { info -> _state.update { it.copy(accounts = it.accounts + (t to info)) } }
                } finally {
                    if (accountLoads[t] === self) accountLoads.remove(t)
                }
            }
        }
    }

    // ---- provider setup -----------------------------------------------------------------

    /**
     * Checks the key against the service with a test call and only then stores it.
     * Returns an error message to show, or null if saved. The stored key is never handed back to the UI.
     */
    suspend fun saveConfig(type: ProviderType, token: String, baseUrl: String): String? {
        val key = token.trim()
        if (key.isEmpty()) return context.getString(R.string.vm_empty_api_key_box)
        val base = baseUrl.trim().ifEmpty { null }
        if (base != null && !Http.isValidHttpsBase(base)) {
            return context.getString(R.string.vm_invalid_selfost_url)
        }
        val config = ProviderConfig(token = key, baseUrl = base)

        // Nothing is stored unless this succeeds, so a typo never ends up as a "connected" provider.
        val check = attempt { Providers.create(type, config, context).verify() }
        check.exceptionOrNull()?.let { e ->
            // The generic "key may have been revoked" text makes no sense for a key just typed in.
            return if (e is ProviderException && e.kind == Kind.AUTH && e.httpCode != null) {
                context.getString(R.string.vm_new_key_rejected, type.label)
            } else {
                describe(type, e)
            }
        }

        // Not cancellable: leaving the screen right after the check must not drop a verified key.
        val configured = withContext(Dispatchers.IO + NonCancellable) {
            store.save(type, config)
            store.configuredTypes()
        }
        providers.remove(type)
        _state.update { it.copy(configured = configured, errors = it.errors - type) }
        refresh(type)
        refreshAccounts(type)
        return null
    }

    fun removeProvider(type: ProviderType) {
        store.clear(type)
        providers.remove(type)
        loads.remove(type)?.cancel()
        cache.remove(type)
        accountLoads.remove(type)?.cancel()
        _state.update {
            it.copy(
                accounts = it.accounts - type,
                configured = store.configuredTypes(),
                errors = it.errors - type,
                aliases = merged(),
                filter = if (it.filter == type) null else it.filter,
            )
        }
    }

    // ---- alias actions --------------------------------------------------------------------

    /** Domains/suffixes the provider currently offers, or an error message. Fetched per dialog open. */
    suspend fun createOptions(type: ProviderType, suffixStyle: String? = null): Pair<CreateOptions?, String?> {
        val result = attempt { provider(type).createOptions(suffixStyle) }
        return result.getOrNull() to result.exceptionOrNull()?.let { describe(type, it) }
    }

    fun create(type: ProviderType, spec: CreateRequest) {
        viewModelScope.launch {
            attempt { provider(type).create(spec) }
                .onSuccess {
                    cache[type] = listOf(it) + cache[type].orEmpty()
                    _state.update { s -> s.copy(aliases = merged()) }
                    say(context.getString(R.string.vm_alias_created, it.address), copy = it.address)
                }
                .onFailure { say(describe(type, it)) }
        }
    }

    fun toggle(alias: Alias) = mutate(alias) { provider(alias.provider).setEnabled(alias, !alias.enabled) }

    fun updateNote(alias: Alias, note: String) =
        mutate(alias) { provider(alias.provider).updateNote(alias, note) }

    fun delete(alias: Alias) {
        if (alias.key in _state.value.busy) return
        setBusy(alias.key, true)
        viewModelScope.launch {
            attempt { provider(alias.provider).delete(alias) }
                .onSuccess {
                    cache[alias.provider] = cache[alias.provider].orEmpty().filter { a -> a.id != alias.id }
                    _state.update { s -> s.copy(aliases = merged()) }
                    say(context.getString(R.string.vm_alias_deleted, alias.address))
                }
                .onFailure { say(describe(alias.provider, it)) }
            setBusy(alias.key, false)
        }
    }

    fun setFilter(type: ProviderType?) = _state.update { it.copy(filter = type) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    // ---- helpers ----------------------------------------------------------------------------

    /** Runs one alias change, ignoring taps while the same alias is still busy (a double tap
     *  on SimpleLogin's toggle would otherwise flip it back). */
    private fun mutate(alias: Alias, op: suspend () -> Alias) {
        if (alias.key in _state.value.busy) return
        setBusy(alias.key, true)
        viewModelScope.launch {
            attempt { op() }
                .onSuccess { updated ->
                    cache[updated.provider] = cache[updated.provider].orEmpty()
                        .map { a -> if (a.id == updated.id) updated else a }
                    _state.update { s -> s.copy(aliases = merged()) }
                }
                .onFailure { say(describe(alias.provider, it)) }
            setBusy(alias.key, false)
        }
    }

    private fun setBusy(key: String, on: Boolean) =
        _state.update { it.copy(busy = if (on) it.busy + key else it.busy - key) }

    private suspend fun provider(type: ProviderType): AliasProvider =
        providers[type] ?: withContext(Dispatchers.IO) {
            val config = store.get(type)
                ?: throw ProviderException(Kind.AUTH, context.getString(R.string.vm_not_connected))
            Providers.create(type, config, getApplication<Application>())
                .also { providers[type] = it }
        }

    private fun merged(): List<Alias> =
        cache.values.flatten().sortedByDescending { it.createdAtMillis ?: 0L }

    private fun say(text: String, copy: String? = null) {
        _messages.trySend(Message(text, copy))
    }

    private inline fun <T> attempt(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun describe(type: ProviderType, e: Throwable): String {
        val name = type.label
        if (e !is ProviderException) return "$name: ${e.message ?: "unexpected error"}"
        return when (e.kind) {
            // No HTTP code means the key was never stored, not that the server rejected it.
            Kind.AUTH ->
                if (e.httpCode == null && e.detail != null) "$name: ${e.detail}"
                else context.getString(R.string.vm_invalid_api_key_msg, name)
            Kind.RATE_LIMIT -> context.getString(
                R.string.vm_rate_limit_msg,
                name,
                context.getString(R.string.http_rate_limited) +
                    (e.retryAfter?.let { context.getString(R.string.http_retry_later, it) } ?: ""),
            )
            Kind.NETWORK -> context.getString(R.string.vm_generic_error, name, e.message)
            Kind.BAD_RESPONSE -> context.getString(R.string.vm_broken_api, name)
            Kind.FORBIDDEN -> "$name: ${e.detail ?: context.getString(R.string.http_not_allowed)}"
            Kind.NOT_FOUND -> "$name: ${e.detail ?: context.getString(R.string.http_not_found)}"
            Kind.SERVER -> "$name: ${e.detail ?: context.getString(R.string.http_server_error, e.httpCode ?: 0)}"
            Kind.OTHER -> "$name: ${e.detail ?: "HTTP ${e.httpCode}"}"
        }
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_SYSTEM_FONT = "system_font"
    }
}
