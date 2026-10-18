package com.dawidggs.aliashub.data

import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Firefox Relay. This is the web app's own API, not an officially supported public one,
 * so field names or paths can change without notice. Everything is parsed defensively and a
 * shape change surfaces as a BAD_RESPONSE error for this provider only.
 */
class RelayProvider(private val config: ProviderConfig) : AliasProvider {
    override val type = ProviderType.RELAY

    private val base = "https://relay.firefox.com/api/v1"

    private fun request(path: String) = Request.Builder()
        .url(base + path)
        .header("Authorization", "Token ${config.token}")
        .header("Accept", "application/json")

    override suspend fun verify() {
        request("/relayaddresses/").send()
    }

    override suspend fun list(): List<Alias> {
        val text = request("/relayaddresses/").send()
        return Http.parsing {
            val t = text.trim()
            // Plain array today; also accept a paginated {"results": [...]} wrapper.
            val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).getJSONArray("results")
            List(arr.length()) { i -> parse(arr.getJSONObject(i)) }
        }
    }

    /**
     * `/profiles/` is a one-element list with the plan flags and totals. The free mask cap is not
     * in the profile; it comes from the public `/runtime_data` (`MAX_NUM_FREE_ALIASES`) and is
     * only shown for free accounts. If that call fails the count is shown without a cap.
     */
    override suspend fun accountInfo(): AccountInfo {
        val profileText = request("/profiles/").send()
        val p = Http.parsing {
            val t = profileText.trim()
            val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).getJSONArray("results")
            arr.getJSONObject(0)
        }
        val premium = p.optBoolean("has_premium", false)

        val freeCap = if (premium) null else try {
            val text = request("/runtime_data/").send()
            Http.parsing { JSONObject(text).longOrNull("MAX_NUM_FREE_ALIASES") }
        } catch (e: ProviderException) {
            if (e.kind == ProviderException.Kind.AUTH) throw e
            null
        }

        val features = buildList {
            if (p.optBoolean("has_phone", false)) add(PlanFeature.PHONE)
            if (p.optBoolean("has_vpn", false)) add(PlanFeature.VPN)
            if (p.optBoolean("has_megabundle", false)) add(PlanFeature.BUNDLE)
        }
        val items = mutableListOf<AccountItem>()
        p.str("subdomain").takeIf { it.isNotBlank() }?.let { items += AccountItem.Text(AccountField.SUBDOMAIN, it) }
        p.longOrNull("total_masks")?.let { items += AccountItem.Count(AccountField.ALIASES, it, limit = freeCap) }
        p.longOrNull("emails_forwarded")?.let { items += AccountItem.Count(AccountField.FORWARDED, it) }
        p.longOrNull("emails_blocked")?.let { items += AccountItem.Count(AccountField.BLOCKED, it) }
        p.longOrNull("emails_replied")?.let { items += AccountItem.Count(AccountField.REPLIED, it) }
        return AccountInfo(
            plan = AccountPlan(
                paid = premium,
                sinceMillis = parseMillis(p.str("date_subscribed")),
                features = features,
            ),
            items = items,
        )
    }

    // Relay masks have no domain or name to choose.
    override suspend fun createOptions(suffixStyle: String?) = CreateOptions(domains = emptyList(), defaultId = null)

    override suspend fun create(spec: CreateRequest): Alias {
        val body = JSONObject().apply { if (spec.note.isNotBlank()) put("description", spec.note) }
        val text = request("/relayaddresses/").send("POST", Http.jsonBody(body))
        return Http.parsing { parse(JSONObject(text)) }
    }

    override suspend fun setEnabled(alias: Alias, enabled: Boolean): Alias {
        val body = JSONObject().put("enabled", enabled)
        request("/relayaddresses/${alias.id}/").send("PATCH", Http.jsonBody(body))
        return alias.copy(enabled = enabled)
    }

    override suspend fun updateNote(alias: Alias, note: String): Alias {
        val body = JSONObject().put("description", note)
        request("/relayaddresses/${alias.id}/").send("PATCH", Http.jsonBody(body))
        return alias.copy(note = note)
    }

    override suspend fun delete(alias: Alias) {
        request("/relayaddresses/${alias.id}/").send("DELETE")
    }

    private fun parse(o: JSONObject) = Alias(
        provider = type,
        id = o.get("id").toString(),
        address = o.str("full_address").ifBlank { o.getString("address") },
        enabled = o.optBoolean("enabled", true),
        note = o.str("description"),
        createdAtMillis = parseMillis(o.str("created_at")),
        forwarded = o.int("num_forwarded"),
        blocked = o.int("num_blocked"),
    )
}
