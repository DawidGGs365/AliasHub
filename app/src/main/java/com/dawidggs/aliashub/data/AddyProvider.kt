package com.dawidggs.aliashub.data

import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.plusAssign

/**
 * Addy.io REST API (v1). Auth is a bearer token. Self-hosted instances work by
 * changing [ProviderConfig.baseUrl]. Which domains an account may create aliases on
 * depends on its plan, so they are fetched from the API rather than hard-coded.
 */
class AddyProvider(private val config: ProviderConfig) : AliasProvider {
    override val type = ProviderType.ADDY

    private val base = Http.baseUrl(config.baseUrl, "https://app.addy.io") + "/api/v1"

    private fun request(path: String) = Request.Builder()
        .url(base + path)
        .header("Authorization", "Bearer ${config.token}")
        .header("Accept", "application/json")
        .header("X-Requested-With", "XMLHttpRequest")

    override suspend fun list(): List<Alias> {
        val out = mutableListOf<Alias>()
        var page = 1
        while (page <= MAX_PAGES) {
            val text = Http.execute(
                request("/aliases?page[size]=$PAGE_SIZE&page[number]=$page").get().build(),
            )
            val data = Http.parsing { JSONObject(text).getJSONArray("data") }
            Http.parsing { for (i in 0 until data.length()) out += parse(data.getJSONObject(i)) }
            if (data.length() < PAGE_SIZE) break
            page++
        }
        return out
    }

    override suspend fun createOptions(): CreateOptions {
        return try {
            val text = Http.execute(request("/domain-options").get().build())
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
                fallback("the response had no domains")
            } else {
                val username = accountUsername()
                CreateOptions(
                    domains = domains.map { d ->
                        // Username domains (name.anonaddy.com) only accept a custom name.
                        val needsName = isUsernameDomain(d, domains, username)
                        DomainOption(
                            id = d,
                            label = d,
                            needsPrefix = needsName,
                            prefixRequired = needsName,
                            joiner = "@",
                        )
                    },
                    defaultId = preferred?.takeIf { it in domains } ?: domains.first(),
                )
            }
        } catch (e: ProviderException) {
            if (e.kind == ProviderException.Kind.AUTH) throw e
            fallback(e.message ?: "unknown error")
        }
    }

    /** The account's username, used to recognise its own subdomains. Null if it can't be read. */
    private suspend fun accountUsername(): String? = try {
        val text = Http.execute(request("/account-details").get().build())
        Http.parsing { JSONObject(text).getJSONObject("data").str("username").ifBlank { null } }
    } catch (e: ProviderException) {
        if (e.kind == ProviderException.Kind.AUTH) throw e
        null
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
        domains = FALLBACK_DOMAINS.map { DomainOption(id = it, label = it) },
        defaultId = FALLBACK_DOMAINS.first(),
        warning = "Couldn't load your domain list ($reason), so only ${
            FALLBACK_DOMAINS.joinToString(
                " and "
            )
        } are offered.",
    )

    override suspend fun create(spec: CreateRequest): Alias {
        val domain = spec.domainId?.takeIf { it.isNotBlank() } ?: FALLBACK_DOMAINS.first()
        val body = JSONObject().put("domain", domain)
        val localPart = spec.prefix.trim().lowercase()
        if (localPart.isNotEmpty()) {
            body.put("format", "custom")
            body.put("local_part", localPart)
        } else {
            body.put("format", "random_characters")
        }
        if (spec.note.isNotBlank()) body.put("description", spec.note)
        val text = Http.execute(request("/aliases").post(Http.jsonBody(body)).build())
        return Http.parsing { parse(JSONObject(text).getJSONObject("data")) }
    }

    override suspend fun setEnabled(alias: Alias, enabled: Boolean): Alias {
        if (alias.enabled == enabled) return alias
        if (enabled) {
            val body = JSONObject().put("id", alias.id)
            Http.execute(request("/active-aliases").post(Http.jsonBody(body)).build())
        } else {
            Http.execute(request("/active-aliases/${alias.id}").delete().build())
        }
        return alias.copy(enabled = enabled)
    }

    override suspend fun updateNote(alias: Alias, note: String): Alias {
        val body = JSONObject().put("description", note)
        Http.execute(request("/aliases/${alias.id}").patch(Http.jsonBody(body)).build())
        return alias.copy(note = note)
    }

    override suspend fun delete(alias: Alias) {
        Http.execute(request("/aliases/${alias.id}").delete().build())
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
        val FALLBACK_DOMAINS = listOf("anonaddy.me", "anonaddy.com")
    }
}
