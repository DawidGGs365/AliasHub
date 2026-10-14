package com.dawidggs.aliashub.data

import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * SimpleLogin REST API. Auth is the raw key in an `Authentication` header.
 * Self-hosted instances work by changing [ProviderConfig.baseUrl].
 */
class SimpleLoginProvider(private val config: ProviderConfig) : AliasProvider {
    override val type = ProviderType.SIMPLELOGIN

    private val base = Http.baseUrl(config.baseUrl, "https://app.simplelogin.io")

    private fun request(path: String) = Request.Builder()
        .url(base + path)
        .header("Authentication", config.token)
        .header("Accept", "application/json")

    override suspend fun list(): List<Alias> {
        val out = mutableListOf<Alias>()
        var page = 0
        // The list endpoint is paginated; an empty page marks the end.
        while (page < MAX_PAGES) {
            val text = Http.execute(request("/api/v2/aliases?page_id=$page").get().build())
            val arr = Http.parsing { JSONObject(text).optJSONArray("aliases") } ?: break
            if (arr.length() == 0) break
            Http.parsing { for (i in 0 until arr.length()) out += parse(arr.getJSONObject(i)) }
            page++
        }
        return out
    }

    override suspend fun createOptions(): CreateOptions {
        val text = Http.execute(request("/api/v5/alias/options").get().build())
        val root = Http.parsing { JSONObject(text) }

        // Free accounts can't use premium suffixes, so hide them when the plan is known.
        // If the plan can't be read, everything is shown and the server decides.
        val premium = try {
            val info = Http.execute(request("/api/user_info").get().build())
            Http.parsing { JSONObject(info).optBoolean("is_premium", true) }
        } catch (e: ProviderException) {
            if (e.kind == ProviderException.Kind.AUTH) throw e
            true
        }

        val suffixes = Http.parsing {
            val arr = root.optJSONArray("suffixes") ?: JSONArray()
            val out = mutableListOf<DomainOption>()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                // The docs spell this key both ways.
                val signed = s.str("signed_suffix").ifBlank { s.str("signed-suffix") }
                if (signed.isBlank()) continue
                if (s.optBoolean("is_premium", false) && !premium) continue
                out += DomainOption(id = signed, label = s.str("suffix"), needsPrefix = true)
            }
            out
        }

        val random = DomainOption(RANDOM_ID, "Random (SimpleLogin picks the name)")
        return CreateOptions(
            domains = listOf(random) + suffixes,
            defaultId = RANDOM_ID,
            warning = if (root.optBoolean("can_create", true)) null
            else "SimpleLogin says you can't create more aliases on your current plan.",
        )
    }

    override suspend fun create(spec: CreateRequest): Alias {
        val body = JSONObject()
        if (spec.note.isNotBlank()) body.put("note", spec.note)

        val path: String
        if (spec.domainId == null || spec.domainId == RANDOM_ID) {
            // Uses the random-alias setting from the user's SimpleLogin account.
            path = "/api/alias/random/new"
        } else {
            val prefix = spec.prefix.trim().lowercase()
                .ifEmpty { UUID.randomUUID().toString().replace("-", "").take(10) }
            body.put("alias_prefix", prefix)
            body.put("signed_suffix", spec.domainId)
            body.put("mailbox_ids", JSONArray().put(preferredMailboxId()))
            path = "/api/v3/alias/custom/new"
        }
        val text = Http.execute(request(path).post(Http.jsonBody(body)).build())
        return Http.parsing { parse(JSONObject(text)) }
    }

    /** Custom aliases must name a mailbox, so use the account's default one. */
    private suspend fun preferredMailboxId(): Long {
        val text = Http.execute(request("/api/v2/mailboxes").get().build())
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
            preferred ?: first ?: throw ProviderException(
                ProviderException.Kind.OTHER,
                "no mailbox found on your SimpleLogin account",
            )
        }
    }

    override suspend fun setEnabled(alias: Alias, enabled: Boolean): Alias {
        if (alias.enabled == enabled) return alias
        // SimpleLogin only offers a toggle, so read the resulting state from the reply.
        val text = Http.execute(request("/api/aliases/${alias.id}/toggle").post(Http.EMPTY_BODY).build())
        val now = Http.parsing { JSONObject(text).getBoolean("enabled") }
        return alias.copy(enabled = now)
    }

    override suspend fun updateNote(alias: Alias, note: String): Alias {
        val body = JSONObject().put("note", note)
        Http.execute(request("/api/aliases/${alias.id}").patch(Http.jsonBody(body)).build())
        return alias.copy(note = note)
    }

    override suspend fun delete(alias: Alias) {
        Http.execute(request("/api/aliases/${alias.id}").delete().build())
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
    }
}
