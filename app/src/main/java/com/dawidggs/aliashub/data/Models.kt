package com.dawidggs.aliashub.data

enum class ProviderType(val label: String) {
    SIMPLELOGIN("SimpleLogin"),
    ADDY("Addy.io"),
    RELAY("Firefox Relay"),
}

/** How the app picks its colors. OLED is dark with a pure black background. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, OLED }

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
    /** True when the user can type the part before the "@" (SimpleLogin suffixes, Addy username domains, anonaddy.com/.me). */
    val needsPrefix: Boolean = false,
    /** True when that name can't be left blank (Addy username domains only accept custom names). */
    val prefixRequired: Boolean = false,
    /** What goes between the name and [label] in the preview: "" when the label already starts with "." or "@". */
    val joiner: String = "",
)

/**
 * One way a provider can come up with the alias name (random characters, words, UUID, a name
 * you type...). [takesName] means the user types the name; [takesDomain] means the domain /
 * suffix choice applies (SimpleLogin's random modes pick their own).
 */
data class GeneratorOption(
    val id: String,
    val label: String,
    val takesName: Boolean = false,
    val takesDomain: Boolean = true,
    /** The domain choice for this generator comes from [CreateOptions.randomDomains], not [CreateOptions.domains]. */
    val randomDomain: Boolean = false,
)

/** A plain labeled choice, e.g. SimpleLogin's suffix style. */
data class ChoiceOption(val id: String, val label: String)

/** What a provider offers when creating an alias, fetched fresh each time the dialog opens. */
data class CreateOptions(
    val domains: List<DomainOption>,
    val defaultId: String?,
    val warning: String? = null,
    /** Empty when the provider has a single way of naming aliases (Relay). */
    val generators: List<GeneratorOption> = emptyList(),
    val defaultGeneratorId: String? = null,
    /** Domains random (non-custom) aliases can use; SimpleLogin only. */
    val randomDomains: List<DomainOption> = emptyList(),
    val defaultRandomDomainId: String? = null,
    /** How the ".xxxxx" part of a custom alias is made; empty when the provider has no such choice. */
    val suffixStyles: List<ChoiceOption> = emptyList(),
    /** The style [domains] were generated with. */
    val suffixStyleId: String? = null,
)

data class CreateRequest(
    val note: String,
    /** Null means "let the provider pick its default". */
    val domainId: String?,
    val prefix: String,
    /** One of [CreateOptions.generators]; null means the provider's own default. */
    val generatorId: String? = null,
    /** SimpleLogin suffix style chosen in the sheet (applies to every generator); null keeps the account's own. */
    val suffixStyle: String? = null,
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

/** Which bit of an account an [AccountItem] describes; the UI maps each one to a localized label. */
enum class AccountField {
    EMAIL, USERNAME, SUBDOMAIN,
    ALIASES, SHARED_ALIASES, RECIPIENTS, DOMAINS, USERNAMES, BANDWIDTH,
    FORWARDED, BLOCKED, REPLIED,
}

/** Extras a paid plan can include (only Relay reports these). */
enum class PlanFeature { PHONE, VPN, BUNDLE }

/**
 * The plan an account is on. [name] is the provider's own plan name when it has one (Addy:
 * "free", "lite", "pro"); otherwise the UI words it from [paid]. [endsAtMillis] is the end of the
 * subscription or of the [trial]; [sinceMillis] is when it started. Anything unknown stays null.
 */
data class AccountPlan(
    val paid: Boolean,
    val name: String? = null,
    val trial: Boolean = false,
    val endsAtMillis: Long? = null,
    val sinceMillis: Long? = null,
    val features: List<PlanFeature> = emptyList(),
)

/**
 * One line of account info. Providers only emit the lines their API actually reports, so there
 * is never a placeholder for something a service doesn't expose.
 */
sealed interface AccountItem {
    val field: AccountField

    data class Text(override val field: AccountField, val value: String) : AccountItem

    /** [limit] null means no cap is known; [unlimited] means the service says there is none. */
    data class Count(
        override val field: AccountField,
        val used: Long,
        val limit: Long? = null,
        val unlimited: Boolean = false,
    ) : AccountItem

    /** Same as [Count] but measured in bytes. */
    data class Bytes(
        override val field: AccountField,
        val used: Long,
        val limit: Long? = null,
        val unlimited: Boolean = false,
    ) : AccountItem
}

data class AccountInfo(
    val plan: AccountPlan?,
    val items: List<AccountItem>,
)

/** Every service implements this; the UI never talks to a service directly. */
interface AliasProvider {
    val type: ProviderType
    /** One cheap authenticated call; throws [ProviderException] (AUTH for a bad key) if it fails. */
    suspend fun verify()
    suspend fun list(): List<Alias>
    /** Plan, limits and usage as far as this service's API reports them. */
    suspend fun accountInfo(): AccountInfo
    /**
     * [suffixStyle] (SimpleLogin) asks for custom-alias suffixes of that style; null keeps the account's own.
     * [account] is the account info the app already has; when given, the plan/username are taken
     * from it instead of being requested again.
     */
    suspend fun createOptions(suffixStyle: String? = null, account: AccountInfo? = null): CreateOptions
    suspend fun create(spec: CreateRequest): Alias
    suspend fun setEnabled(alias: Alias, enabled: Boolean): Alias
    suspend fun updateNote(alias: Alias, note: String): Alias
    suspend fun delete(alias: Alias)
}
