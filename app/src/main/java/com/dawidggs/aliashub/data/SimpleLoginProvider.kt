package com.dawidggs.aliashub.data

import com.dawidggs.aliashub.R
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    override suspend fun createOptions(): CreateOptions = coroutineScope {
        // Independent requests, so run them side by side. The mailbox is only needed later by
        // create(), but fetching it now removes a round trip from the "Create" tap.
        val optionsReq = async { request("/api/v5/alias/options").send() }
        val premiumReq = async { isPremium() }
        val mailboxReq = async { prefetchMailbox() }

        val root = Http.parsing { JSONObject(optionsReq.await()) }
        val premium = premiumReq.await()
        mailboxReq.await()

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

        val random = DomainOption(RANDOM_ID, context.getString(R.string.sl_random_option))
        CreateOptions(
            domains = listOf(random) + suffixes,
            defaultId = RANDOM_ID,
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
        val text = request(path).send("POST", Http.jsonBody(body))
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
    }
}
