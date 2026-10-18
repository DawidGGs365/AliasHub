package com.dawidggs.aliashub.data

import com.dawidggs.aliashub.R
import kotlinx.coroutines.async
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import android.content.Context

/**
 * SimpleLogin REST API. Auth is the raw key in an `Authentication` header.
 * Self-hosted instances work by changing [ProviderConfig.baseUrl].
 */
class SimpleLoginProvider(
        private val config: ProviderConfig,
        private val context: Context) : AliasProvider {
    override val type = ProviderType.SIMPLELOGIN

    private val base = Http.baseUrl(config.baseUrl, "https://app.simplelogin.io")

    /** The account's default mailbox; providers are cached per config, so this survives between calls. */
    @Volatile
    private var mailboxId: Long? = null

    private fun request(path: String) = Request.Builder()
        .url(base + path)
        .header("Authentication", config.token)
        .header("Accept", "application/json")

    override suspend fun verify() {
        request("/api/user_info").send()
    }

    override suspend fun list(): List<Alias> {
        val out = mutableListOf<Alias>()
        var page = 0
        // The list endpoint is paginated; an empty page marks the end.
        while (page < MAX_PAGES) {
            val text = request("/api/v2/aliases?page_id=$page").send()
            val arr = Http.parsing { JSONObject(text).optJSONArray("aliases") } ?: break
            if (arr.length() == 0) break
            Http.parsing { for (i in 0 until arr.length()) out += parse(arr.getJSONObject(i)) }
            page++
        }
        return out
    }

    /**
     * `/api/user_info` gives the plan (and trial end), `/api/stats` the totals. The free-plan alias
     * cap is only documented for free accounts, so premium shows the plain alias count.
     */
    override suspend fun accountInfo(): AccountInfo = coroutineScope {
        val infoReq = async { request("/api/user_info").send() }
        // The stats are a bonus; a failure there must not hide the plan.
        val statsReq = async {
            try {
                request("/api/stats").send()
            } catch (e: ProviderException) {
                if (e.kind == ProviderException.Kind.AUTH) throw e
                null
            }
        }
        val info = Http.parsing { JSONObject(infoReq.await()) }
        val stats = statsReq.await()?.let { text -> Http.parsing { JSONObject(text) } }

        val premium = info.optBoolean("is_premium", false)
        val trial = info.optBoolean("in_trial", false)
        val items = mutableListOf<AccountItem>()
        info.str("email").takeIf { it.isNotBlank() }?.let { items += AccountItem.Text(AccountField.EMAIL, it) }
        stats?.longOrNull("nb_alias")?.let { count ->
            items += AccountItem.Count(
                AccountField.ALIASES,
                used = count,
                limit = if (premium) null else info.longOrNull("max_alias_free_plan"),
            )
        }
        stats?.longOrNull("nb_forward")?.let { items += AccountItem.Count(AccountField.FORWARDED, it) }
        stats?.longOrNull("nb_block")?.let { items += AccountItem.Count(AccountField.BLOCKED, it) }
        stats?.longOrNull("nb_reply")?.let { items += AccountItem.Count(AccountField.REPLIED, it) }
        AccountInfo(
            plan = AccountPlan(
                paid = premium,
                trial = trial,
                endsAtMillis = if (trial) info.secondsToMillis("trial_end_timestamp") else null,
            ),
            items = items,
        )
    }

    /** Account settings can only be changed one at a time from this app. */
    private val settingsLock = Mutex()

    private suspend fun readSettings(): JSONObject? = try {
        val text = request("/api/setting").send()
        Http.parsing { JSONObject(text) }
    } catch (e: ProviderException) {
        if (e.kind == ProviderException.Kind.AUTH) throw e
        null
    }

    /**
     * The API has no per-request choice for the suffix style or the domain of random aliases, only
     * these account settings. So the wanted values are applied just for [block] and the previous
     * ones are put back right after (in one PATCH each way), even if [block] fails. Keys whose
     * value is already set, or whose current value is unknown (nothing to restore), are skipped.
     */
    private suspend fun <T> withSettings(
        wanted: Map<String, String?>,
        current: JSONObject?,
        block: suspend () -> T,
    ): T {
        val change = wanted.mapNotNull { (key, value) ->
            val old = current?.str(key)?.ifBlank { null }
            if (value.isNullOrBlank() || old == null || value == old) null else Triple(key, value, old)
        }
        if (change.isEmpty()) return block()
        return settingsLock.withLock {
            val apply = JSONObject().also { o -> change.forEach { (k, v, _) -> o.put(k, v) } }
            val restore = JSONObject().also { o -> change.forEach { (k, _, old) -> o.put(k, old) } }
            request("/api/setting").send("PATCH", Http.jsonBody(apply))
            try {
                block()
            } finally {
                withContext(NonCancellable) {
                    try {
                        request("/api/setting").send("PATCH", Http.jsonBody(restore))
                    } catch (e: ProviderException) {
                        // Best effort: nothing more can be done from here.
                    }
                }
            }
        }
    }

    /** Domains random aliases can be created on. Best effort: without it the account default is used. */
    private suspend fun randomDomains(): List<DomainOption> = try {
        val text = request("/api/v2/setting/domains").send()
        Http.parsing {
            val arr = JSONArray(text)
            List(arr.length()) { i -> arr.getJSONObject(i).str("domain") }
                .filter { it.isNotBlank() }
                .map { DomainOption(id = it, label = it) }
        }
    } catch (e: ProviderException) {
        if (e.kind == ProviderException.Kind.AUTH) throw e
        emptyList()
    }

    override suspend fun createOptions(suffixStyle: String?, account: AccountInfo?): CreateOptions = coroutineScope {
        // Independent requests, so run them side by side. The mailbox is only needed later by
        // create(), but fetching it now removes a round trip from the "Create" tap.
        val settingsReq = async { readSettings() }
        val domainsReq = async { randomDomains() }
        // The plan is already known from the account info; only ask again when it isn't.
        val premiumReq = async { account?.plan?.paid ?: isPremium() }
        val mailboxReq = async { prefetchMailbox() }

        val settings = settingsReq.await()
        val currentStyle = settings?.str("random_alias_suffix")?.ifBlank { null }
        // The suffix list depends only on the account's suffix setting, so ask for another style
        // by switching that setting for the duration of this one call.
        val optionsText = withSettings(mapOf("random_alias_suffix" to suffixStyle), settings) {
            request("/api/v5/alias/options").send()
        }
        val root = Http.parsing { JSONObject(optionsText) }
        val premium = premiumReq.await()
        mailboxReq.await()
        val randomDomains = domainsReq.await()

        val suffixes = Http.parsing {
            val arr = root.optJSONArray("suffixes") ?: JSONArray()
            val out = mutableListOf<DomainOption>()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                // The docs spell this key both ways.
                val signed = s.str("signed_suffix").ifBlank { s.str("signed-suffix") }
                if (signed.isBlank()) continue
                if (s.optBoolean("is_premium", false) && !premium) continue
                // "suffix" looks like ".xk3d9@domain.com". The generated part is hidden on purpose,
                // so only the domain is shown (same as the random modes' domain list).
                val raw = s.str("suffix")
                val domain = if ('@' in raw) raw.substringAfter('@') else raw
                out += DomainOption(id = signed, label = domain, needsPrefix = true, joiner = "@")
            }
            out
        }

        val generators = buildList {
            add(GeneratorOption(RANDOM_ID, context.getString(R.string.sl_gen_default), randomDomain = true))
            add(GeneratorOption(MODE_WORD, context.getString(R.string.gen_random_words), randomDomain = true))
            add(GeneratorOption(MODE_UUID, context.getString(R.string.gen_uuid), randomDomain = true))
            if (suffixes.isNotEmpty()) {
                add(GeneratorOption(CUSTOM_ID, context.getString(R.string.gen_custom), takesName = true))
            }
        }
        val defaultDomain = settings?.str("random_alias_default_domain")?.ifBlank { null }
        CreateOptions(
            domains = suffixes,
            defaultId = suffixes.firstOrNull()?.id,
            generators = generators,
            defaultGeneratorId = RANDOM_ID,
            randomDomains = randomDomains,
            defaultRandomDomainId = defaultDomain?.takeIf { d -> randomDomains.any { it.id == d } }
                ?: randomDomains.firstOrNull()?.id,
            // Only offered when the current setting is known, so it can be put back afterward.
            suffixStyles = if (currentStyle == null) emptyList() else listOf(
                ChoiceOption(STYLE_WORD, context.getString(R.string.sl_suffix_word)),
                ChoiceOption(STYLE_RANDOM, context.getString(R.string.sl_suffix_characters)),
            ),
            suffixStyleId = currentStyle?.let { suffixStyle ?: it },
            warning = if (root.optBoolean("can_create", true)) null
            else context.getString(R.string.sl_limit_reached),
        )
    }

    /**
     * Free accounts can't use premium suffixes, so hide them when the plan is known.
     * If the plan can't be read, everything is shown and the server decides.
     */
    private suspend fun isPremium(): Boolean = try {
        val info = request("/api/user_info").send()
        Http.parsing { JSONObject(info).optBoolean("is_premium", true) }
    } catch (e: ProviderException) {
        if (e.kind == ProviderException.Kind.AUTH) throw e
        true
    }

    /** Best effort: a failure here is retried (and reported) by create() when it matters. */
    private suspend fun prefetchMailbox() {
        try {
            preferredMailboxId()
        } catch (e: ProviderException) {
            if (e.kind == ProviderException.Kind.AUTH) throw e
        }
    }

    override suspend fun create(spec: CreateRequest): Alias {
        val body = JSONObject()
        if (spec.note.isNotBlank()) body.put("note", spec.note)

        val path: String
        when (val generator = spec.generatorId) {
            // Named by the user: needs a suffix and a mailbox.
            CUSTOM_ID -> {
                val prefix = spec.prefix.trim().lowercase()
                    .ifEmpty { UUID.randomUUID().toString().replace("-", "").take(10) }
                body.put("alias_prefix", prefix)
                body.put("signed_suffix", spec.domainId)
                body.put("mailbox_ids", JSONArray().put(preferredMailboxId()))
                path = "/api/v3/alias/custom/new"
            }
            // Explicit mode ("word" / "uuid").
            MODE_WORD, MODE_UUID -> path = "/api/alias/random/new?mode=$generator"
            // Uses the random-alias setting from the user's SimpleLogin account.
            else -> path = "/api/alias/random/new"
        }
        val isRandom = spec.generatorId != CUSTOM_ID
        val wanted = mapOf(
            // Random aliases land on the account's default random domain, so switch it for this call.
            "random_alias_default_domain" to spec.domainId.takeIf { isRandom },
            // The chosen suffix style applies whichever generator is used.
            "random_alias_suffix" to spec.suffixStyle,
        )
        // Only read the settings when there is something to switch.
        val current = if (wanted.values.any { !it.isNullOrBlank() }) readSettings() else null
        val text = withSettings(wanted, current) { request(path).send("POST", Http.jsonBody(body)) }
        return Http.parsing { parse(JSONObject(text)) }
    }

    /** Custom aliases must name a mailbox, so use the account's default one. */
    private suspend fun preferredMailboxId(): Long {
        mailboxId?.let { return it }
        val text = request("/api/v2/mailboxes").send()
        return Http.parsing {
            val arr = JSONObject(text).getJSONArray("mailboxes")
            var first: Long? = null
            var preferred: Long? = null
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val id = m.getLong("id")
                if (first == null) first = id
                if (m.optBoolean("default", false)) preferred = id
            }
            (preferred ?: first ?: throw ProviderException(
                ProviderException.Kind.OTHER,
                context.getString(R.string.sl_no_mailbox_found),
            )).also { mailboxId = it }
        }
    }

    override suspend fun setEnabled(alias: Alias, enabled: Boolean): Alias {
        if (alias.enabled == enabled) return alias
        // SimpleLogin only offers a toggle, so read the resulting state from the reply.
        val text = request("/api/aliases/${alias.id}/toggle").send("POST", Http.EMPTY_BODY)
        val now = Http.parsing { JSONObject(text).getBoolean("enabled") }
        return alias.copy(enabled = now)
    }

    override suspend fun updateNote(alias: Alias, note: String): Alias {
        val body = JSONObject().put("note", note)
        request("/api/aliases/${alias.id}").send("PATCH", Http.jsonBody(body))
        return alias.copy(note = note)
    }

    override suspend fun delete(alias: Alias) {
        request("/api/aliases/${alias.id}").send("DELETE")
    }

    private fun parse(o: JSONObject) = Alias(
        provider = type,
        id = o.get("id").toString(),
        address = o.getString("email"),
        enabled = o.optBoolean("enabled", true),
        note = o.str("note"),
        createdAtMillis = o.optLong("creation_timestamp", 0L).takeIf { it > 0 }?.times(1000),
        forwarded = o.int("nb_forward"),
        blocked = o.int("nb_block"),
    )

    private companion object {
        const val MAX_PAGES = 200
        const val RANDOM_ID = "random"
        const val MODE_WORD = "word"
        const val MODE_UUID = "uuid"
        const val CUSTOM_ID = "custom"
        /** Names of AliasSuffixEnum in the SimpleLogin API. */
        const val STYLE_WORD = "word"
        const val STYLE_RANDOM = "random_string"
    }
}
