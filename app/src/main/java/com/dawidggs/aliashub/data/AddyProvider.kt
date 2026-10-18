package com.dawidggs.aliashub.data

import com.dawidggs.aliashub.R
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import android.content.Context

/**
 * Addy.io REST API (v1). Auth is a bearer token. Self-hosted instances work by
 * changing [ProviderConfig.baseUrl]. Which domains an account may create aliases on
 * depends on its plan, so they are fetched from the API rather than hard-coded.
 */
class AddyProvider(
        private val config: ProviderConfig,
        private val context: Context) : AliasProvider {
    override val type = ProviderType.ADDY

    private val base = Http.baseUrl(config.baseUrl, "https://app.addy.io") + "/api/v1"

    private fun request(path: String) = Request.Builder()
        .url(base + path)
        .header("Authorization", "Bearer ${config.token}")
        .header("Accept", "application/json")
        .header("X-Requested-With", "XMLHttpRequest")

    override suspend fun verify() {
        request("/account-details").send()
    }

    override suspend fun list(): List<Alias> {
        val out = mutableListOf<Alias>()
        var page = 1
        while (page <= MAX_PAGES) {
            val text = request("/aliases?page[size]=$PAGE_SIZE&page[number]=$page").send()
            val data = Http.parsing { JSONObject(text).getJSONArray("data") }
            Http.parsing { for (i in 0 until data.length()) out += parse(data.getJSONObject(i)) }
            if (data.length() < PAGE_SIZE) break
            page++
        }
        return out
    }

    /**
     * Everything comes from `/account-details`. A limit that is null (or, for bandwidth and
     * shared-domain aliases, 0) means unlimited. Limits for recipients, domains and usernames can
     * legitimately be 0 (not on the plan), so those are only shown as "used / limit" when positive.
     * Self-hosted instances don't report a subscription, so then there is simply no plan line.
     */
    override suspend fun accountInfo(): AccountInfo {
        val text = request("/account-details").send()
        val d = Http.parsing { JSONObject(text).getJSONObject("data") }

        val subscription = d.str("subscription").ifBlank { null }
        val plan = subscription?.let {
            AccountPlan(
                paid = !it.equals("free", ignoreCase = true),
                name = it,
                endsAtMillis = parseMillis(d.str("subscription_ends_at")),
            )
        }

        val items = mutableListOf<AccountItem>()
        d.str("username").takeIf { it.isNotBlank() }?.let { items += AccountItem.Text(AccountField.USERNAME, it) }

        d.longOrNull("total_aliases")?.let { total ->
            items += AccountItem.Count(AccountField.ALIASES, used = total)
        }
        d.longOrNull("active_shared_domain_alias_count")?.let { used ->
            val limit = d.longOrNull("active_shared_domain_alias_limit")
            items += AccountItem.Count(
                AccountField.SHARED_ALIASES,
                used = used,
                limit = limit?.takeIf { it > 0 },
                unlimited = limit == null || limit <= 0,
            )
        }
        fun counted(field: AccountField, countKey: String, limitKey: String) {
            val used = d.longOrNull(countKey) ?: return
            val limit = d.longOrNull(limitKey)?.takeIf { it > 0 }
            // A feature that isn't on the plan and isn't used says nothing useful.
            if (used == 0L && limit == null) return
            items += AccountItem.Count(field, used, limit)
        }
        counted(AccountField.RECIPIENTS, "recipient_count", "recipient_limit")
        counted(AccountField.DOMAINS, "active_domain_count", "active_domain_limit")
        counted(AccountField.USERNAMES, "username_count", "username_limit")

        d.longOrNull("bandwidth")?.let { used ->
            val limit = d.longOrNull("bandwidth_limit")
            items += AccountItem.Bytes(
                AccountField.BANDWIDTH,
                used = used,
                limit = limit?.takeIf { it > 0 },
                unlimited = limit == null || limit <= 0,
            )
        }
        d.longOrNull("total_emails_forwarded")?.let { items += AccountItem.Count(AccountField.FORWARDED, it) }
        d.longOrNull("total_emails_blocked")?.let { items += AccountItem.Count(AccountField.BLOCKED, it) }
        d.longOrNull("total_emails_replied")?.let { items += AccountItem.Count(AccountField.REPLIED, it) }
        return AccountInfo(plan, items)
    }

    override suspend fun createOptions(suffixStyle: String?): CreateOptions {
        return try {
            coroutineScope {
                // The username and default format only refine the result, so fetch them alongside
                // the domain list.
                val usernameReq = async { accountSettings() }
                val text = request("/domain-options").send()
                val (domains, preferred) = Http.parsing {
                    val root = JSONObject(text)
                    val arr = root.optJSONArray("data") ?: JSONArray()
                    val names = List(arr.length()) { i ->
                        val v = arr.get(i)
                        if (v is JSONObject) v.str("domain") else v.toString()
                    }.filter { it.isNotBlank() }
                    names to root.str("defaultAliasDomain").ifBlank { null }
                }
                if (domains.isEmpty()) {
                    usernameReq.cancel()
                    fallback(context.getString(R.string.addy_empty_response))
                } else {
                    val (username, defaultFormat) = usernameReq.await()
                    CreateOptions(
                        domains = domains.map { d ->
                            // Username domains (name.anonaddy.com) only accept a custom name;
                            // the shared anonaddy.com / anonaddy.me take one optionally.
                            val usernameDomain = isUsernameDomain(d, domains, username)
                            DomainOption(
                                id = d,
                                label = d,
                                needsPrefix = usernameDomain || d.lowercase() in OPTIONAL_NAME_DOMAINS,
                                prefixRequired = usernameDomain,
                                joiner = "@",
                            )
                        },
                        defaultId = preferred?.takeIf { it in domains } ?: domains.first(),
                        generators = generators(),
                        defaultGeneratorId = defaultFormat?.takeIf { f -> f in FORMATS } ?: FORMATS.first(),
                    )
                }
            }
        } catch (e: ProviderException) {
            if (e.kind == ProviderException.Kind.AUTH) throw e
            fallback(e.message ?: "unknown error")
        }
    }

    /**
     * The account's username (to recognize its own subdomains) and its default alias format.
     * Both are null if they can't be read.
     */
    private suspend fun accountSettings(): Pair<String?, String?> = try {
        val text = request("/account-details").send()
        Http.parsing {
            val d = JSONObject(text).getJSONObject("data")
            d.str("username").ifBlank { null } to d.str("default_alias_format").ifBlank { null }
        }
    } catch (e: ProviderException) {
        if (e.kind == ProviderException.Kind.AUTH) throw e
        null to null
    }

    /** Every format the create endpoint accepts; "custom" is the only one that takes a name. */
    private fun generators(): List<GeneratorOption> = FORMATS.map { f ->
        GeneratorOption(
            id = f,
            label = context.getString(
                when (f) {
                    "random_characters" -> R.string.gen_random_characters
                    "uuid" -> R.string.gen_uuid
                    "random_words" -> R.string.gen_random_words
                    "random_male_name" -> R.string.gen_random_male_name
                    "random_female_name" -> R.string.gen_random_female_name
                    "random_noun" -> R.string.gen_random_noun
                    else -> R.string.gen_custom
                },
            ),
            takesName = f == "custom",
        )
    }

    /**
     * With the username known it's exact. Without it, a domain that is a subdomain of another
     * listed domain (name.anonaddy.com next to anonaddy.com) is treated as a username domain.
     */
    private fun isUsernameDomain(domain: String, all: List<String>, username: String?): Boolean =
        if (username != null) {
            domain.startsWith("$username.", ignoreCase = true)
        } else {
            all.any { other -> other != domain && domain.endsWith(".$other", ignoreCase = true) }
        }

    private fun fallback(reason: String) = CreateOptions(
        domains = FALLBACK_DOMAINS.map {
            DomainOption(id = it, label = it, needsPrefix = true, prefixRequired = false, joiner = "@")
        },
        defaultId = FALLBACK_DOMAINS.first(),
        generators = generators(),
        defaultGeneratorId = FORMATS.first(),
        warning = context.getString(
            R.string.addy_fallback_warning,
            reason
        ),
    )

    override suspend fun create(spec: CreateRequest): Alias {
        val domain = spec.domainId?.takeIf { it.isNotBlank() } ?: FALLBACK_DOMAINS.first()
        val body = JSONObject().put("domain", domain)
        val localPart = spec.prefix.trim().lowercase()
        // Without an explicit generator keep the old rule: a typed name means custom.
        val format = spec.generatorId?.takeIf { it in FORMATS }
            ?: if (localPart.isNotEmpty()) "custom" else "random_characters"
        body.put("format", format)
        if (format == "custom") body.put("local_part", localPart)
        if (spec.note.isNotBlank()) body.put("description", spec.note)
        val text = request("/aliases").send("POST", Http.jsonBody(body))
        return Http.parsing { parse(JSONObject(text).getJSONObject("data")) }
    }

    override suspend fun setEnabled(alias: Alias, enabled: Boolean): Alias {
        if (alias.enabled == enabled) return alias
        if (enabled) {
            val body = JSONObject().put("id", alias.id)
            request("/active-aliases").send("POST", Http.jsonBody(body))
        } else {
            request("/active-aliases/${alias.id}").send("DELETE")
        }
        return alias.copy(enabled = enabled)
    }

    override suspend fun updateNote(alias: Alias, note: String): Alias {
        val body = JSONObject().put("description", note)
        request("/aliases/${alias.id}").send("PATCH", Http.jsonBody(body))
        return alias.copy(note = note)
    }

    override suspend fun delete(alias: Alias) {
        request("/aliases/${alias.id}").send("DELETE")
    }

    private fun parse(o: JSONObject) = Alias(
        provider = type,
        id = o.get("id").toString(),
        address = o.getString("email"),
        enabled = o.optBoolean("active", true),
        note = o.str("description"),
        createdAtMillis = parseMillis(o.str("created_at")),
        forwarded = o.int("emails_forwarded"),
        blocked = o.int("emails_blocked"),
    )

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 200
        /** The `format` values of POST /aliases, in the order they are offered. */
        val FORMATS = listOf(
            "random_characters", "uuid", "random_words",
            "random_male_name", "random_female_name", "random_noun", "custom",
        )
        val FALLBACK_DOMAINS = listOf("anonaddy.me", "anonaddy.com")

        /** Shared domains where a custom name is optional (blank = random characters). */
        val OPTIONAL_NAME_DOMAINS = setOf("anonaddy.me", "anonaddy.com")
    }
}
