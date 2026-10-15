package com.dawidggs.aliashub.data

enum class ProviderType(val label: String) {
    SIMPLELOGIN("SimpleLogin"),
    ADDY("Addy.io"),
    RELAY("Firefox Relay"),
}

/** What the user entered for one provider. [baseUrl] is only set for self-hosted instances. */
data class ProviderConfig(
    val token: String,
    val baseUrl: String? = null,
)

/**
 * One choice in the "new alias" dialog: a domain (Addy) or a suffix (SimpleLogin).
 * [id] is whatever the provider needs back when creating; [label] is what the user sees.
 */
data class DomainOption(
    val id: String,
    val label: String,
    /** True when the user can type the part before the "@" (SimpleLogin suffixes, Addy username domains). */
    val needsPrefix: Boolean = false,
    /** True when that name can't be left blank (Addy username domains only accept custom names). */
    val prefixRequired: Boolean = false,
    /** What goes between the name and [label] in the preview: "" when the label already starts with "." or "@". */
    val joiner: String = "",
)

/** What a provider offers when creating an alias, fetched fresh each time the dialog opens. */
data class CreateOptions(
    val domains: List<DomainOption>,
    val defaultId: String?,
    val warning: String? = null,
)

data class CreateRequest(
    val note: String,
    /** Null means "let the provider pick its default". */
    val domainId: String?,
    val prefix: String,
)

/** The one alias shape the whole UI works with, whichever service it came from. */
data class Alias(
    val provider: ProviderType,
    val id: String,
    val address: String,
    val enabled: Boolean,
    val note: String,
    val createdAtMillis: Long?,
    val forwarded: Int,
    val blocked: Int,
) {
    /** Unique across providers (two services can reuse the same numeric id). */
    val key: String get() = "${provider.name}:$id"
}

/**
 * Carries no user-facing text of its own for HTTP failures: [detail] is whatever the server said
 * (if anything) and the UI layer turns [kind] into a localized message.
 */
class ProviderException(
    val kind: Kind,
    val detail: String? = null,
    val httpCode: Int? = null,
    val retryAfter: String? = null,
) : Exception(detail) {
    enum class Kind { AUTH, FORBIDDEN, NOT_FOUND, RATE_LIMIT, SERVER, NETWORK, BAD_RESPONSE, OTHER }
}

/** Every service implements this; the UI never talks to a service directly. */
interface AliasProvider {
    val type: ProviderType
    suspend fun list(): List<Alias>
    suspend fun createOptions(): CreateOptions
    suspend fun create(spec: CreateRequest): Alias
    suspend fun setEnabled(alias: Alias, enabled: Boolean): Alias
    suspend fun updateNote(alias: Alias, note: String): Alias
    suspend fun delete(alias: Alias)
}
