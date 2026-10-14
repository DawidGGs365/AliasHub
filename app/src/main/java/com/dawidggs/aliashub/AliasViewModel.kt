package com.dawidggs.aliashub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val configured: List<ProviderType>,
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
    private val store = CredentialStore(app)
    private val cache = mutableMapOf<ProviderType, List<Alias>>()

    private val _state = MutableStateFlow(UiState(configured = store.configuredTypes()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = Channel<Message>(Channel.BUFFERED)
    val messages: Flow<Message> = _messages.receiveAsFlow()

    init {
        refresh()
    }

    // ---- loading ----------------------------------------------------------------------

    /** Reloads every connected provider in parallel, or just [only]. */
    fun refresh(only: ProviderType? = null) {
        val targets = if (only != null) listOf(only) else _state.value.configured
        if (targets.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val results = targets.map { t -> async { t to attempt { provider(t).list() } } }.awaitAll()
            val errors = _state.value.errors.toMutableMap()
            for ((t, result) in results) {
                result
                    .onSuccess { cache[t] = it; errors.remove(t) }
                    .onFailure { errors[t] = describe(t, it) }
            }
            _state.update { it.copy(loading = false, errors = errors, aliases = merged()) }
        }
    }

    // ---- provider setup -----------------------------------------------------------------

    /** Returns an error message to show, or null if saved. The stored key is never handed back to the UI. */
    fun saveConfig(type: ProviderType, token: String, baseUrl: String): String? {
        val key = token.trim()
        if (key.isEmpty()) return "Paste an API key first."
        val base = baseUrl.trim().ifEmpty { null }
        if (base != null && !Http.isValidHttpsBase(base)) {
            return "Self-hosted URL must be a valid https:// address."
        }
        store.save(type, ProviderConfig(token = key, baseUrl = base))
        _state.update { it.copy(configured = store.configuredTypes(), errors = it.errors - type) }
        refresh(type)
        return null
    }

    fun removeProvider(type: ProviderType) {
        store.clear(type)
        cache.remove(type)
        _state.update {
            it.copy(
                configured = store.configuredTypes(),
                errors = it.errors - type,
                aliases = merged(),
                filter = if (it.filter == type) null else it.filter,
            )
        }
    }

    // ---- alias actions --------------------------------------------------------------------

    /** Domains/suffixes the provider currently offers, or an error message. Fetched per dialog open. */
    suspend fun createOptions(type: ProviderType): Pair<CreateOptions?, String?> {
        val result = attempt { provider(type).createOptions() }
        return result.getOrNull() to result.exceptionOrNull()?.let { describe(type, it) }
    }

    fun create(type: ProviderType, spec: CreateRequest) {
        viewModelScope.launch {
            attempt { provider(type).create(spec) }
                .onSuccess {
                    cache[type] = listOf(it) + cache[type].orEmpty()
                    _state.update { s -> s.copy(aliases = merged()) }
                    say("Created ${it.address}", copy = it.address)
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
                    say("Deleted ${alias.address}")
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

    private fun provider(type: ProviderType) = Providers.create(
        type,
        store.get(type) ?: throw ProviderException(Kind.AUTH, "not connected"),
    )

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
            Kind.AUTH -> "$name rejected the API key. It may have been revoked, so update it in Providers."
            Kind.RATE_LIMIT -> "$name is rate limiting requests (${e.message}). Try again shortly."
            Kind.NETWORK -> "Couldn't reach $name (${e.message})."
            Kind.BAD_RESPONSE -> "$name returned something unexpected. Its API may have changed."
            else -> "$name: ${e.message}"
        }
    }
}
