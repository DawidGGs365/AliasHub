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

    override suspend fun list(): List<Alias> {
        val text = Http.execute(request("/relayaddresses/").get().build())
        return Http.parsing {
            val t = text.trim()
            // Plain array today; also accept a paginated {"results": [...]} wrapper.
            val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).getJSONArray("results")
            List(arr.length()) { i -> parse(arr.getJSONObject(i)) }
        }
    }

    // Relay masks have no domain or name to choose.
    override suspend fun createOptions() = CreateOptions(domains = emptyList(), defaultId = null)

    override suspend fun create(spec: CreateRequest): Alias {
        val body = JSONObject().apply { if (spec.note.isNotBlank()) put("description", spec.note) }
        val text = Http.execute(request("/relayaddresses/").post(Http.jsonBody(body)).build())
        return Http.parsing { parse(JSONObject(text)) }
    }

    override suspend fun setEnabled(alias: Alias, enabled: Boolean): Alias {
        val body = JSONObject().put("enabled", enabled)
        Http.execute(request("/relayaddresses/${alias.id}/").patch(Http.jsonBody(body)).build())
        return alias.copy(enabled = enabled)
    }

    override suspend fun updateNote(alias: Alias, note: String): Alias {
        val body = JSONObject().put("description", note)
        Http.execute(request("/relayaddresses/${alias.id}/").patch(Http.jsonBody(body)).build())
        return alias.copy(note = note)
    }

    override suspend fun delete(alias: Alias) {
        Http.execute(request("/relayaddresses/${alias.id}/").delete().build())
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
