package com.dawidggs.aliashub.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-disk copy of the alias lists and account details, so the next app start can show them
 * immediately instead of waiting for the network. It holds no secrets (API keys stay in
 * [CredentialStore]) and lives in the app's private storage. Any read problem (missing file,
 * older format, corrupt JSON) simply yields an empty snapshot: the cache is only ever an
 * optimization, never a source of truth.
 */
internal class LocalCache(context: Context) {
    class Snapshot(
        val aliases: Map<ProviderType, List<Alias>>,
        val accounts: Map<ProviderType, AccountInfo>,
        /** When each provider's alias list was last fetched successfully (epoch millis). */
        val syncedAt: Map<ProviderType, Long> = emptyMap(),
    )

    private val dir: File = context.noBackupFilesDir
    private val file = File(dir, FILE_NAME)

    fun load(): Snapshot = try {
        if (file.exists()) decode(file.readText()) else EMPTY
    } catch (e: Exception) {
        EMPTY
    }

    /** Writes via a temp file + rename so a crash mid-write can't leave a half-written cache. */
    @Synchronized
    fun save(snapshot: Snapshot) {
        try {
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(encode(snapshot))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            // Best effort: the next successful sync writes it again.
        }
    }

    // ---- encoding ---------------------------------------------------------------------------

    private fun encode(s: Snapshot): String {
        val aliases = JSONObject()
        s.aliases.forEach { (type, list) ->
            aliases.put(type.name, JSONArray().also { arr -> list.forEach { arr.put(encode(it)) } })
        }
        val accounts = JSONObject()
        s.accounts.forEach { (type, info) -> accounts.put(type.name, encode(info)) }
        val synced = JSONObject()
        s.syncedAt.forEach { (type, millis) -> synced.put(type.name, millis) }
        return JSONObject().put("v", VERSION).put("aliases", aliases).put("accounts", accounts)
            .put("synced", synced).toString()
    }

    private fun encode(a: Alias) = JSONObject()
        .put("id", a.id)
        .put("address", a.address)
        .put("enabled", a.enabled)
        .put("note", a.note)
        .put("created", a.createdAtMillis)
        .put("fwd", a.forwarded)
        .put("blk", a.blocked)

    private fun encode(info: AccountInfo): JSONObject {
        val items = JSONArray()
        info.items.forEach { item ->
            items.put(
                when (item) {
                    is AccountItem.Text -> JSONObject().put("t", "text").put("f", item.field.name).put("value", item.value)
                    is AccountItem.Count -> JSONObject().put("t", "count").put("f", item.field.name)
                        .put("used", item.used).put("limit", item.limit).put("unlimited", item.unlimited)
                    is AccountItem.Bytes -> JSONObject().put("t", "bytes").put("f", item.field.name)
                        .put("used", item.used).put("limit", item.limit).put("unlimited", item.unlimited)
                },
            )
        }
        val plan = info.plan?.let { p ->
            JSONObject()
                .put("paid", p.paid)
                .put("name", p.name)
                .put("trial", p.trial)
                .put("ends", p.endsAtMillis)
                .put("since", p.sinceMillis)
                .put("features", JSONArray().also { arr -> p.features.forEach { arr.put(it.name) } })
        }
        return JSONObject().put("plan", plan).put("items", items)
    }

    // ---- decoding ---------------------------------------------------------------------------

    private fun decode(text: String): Snapshot {
        val root = JSONObject(text)
        if (root.optInt("v", 0) != VERSION) return EMPTY

        val aliases = mutableMapOf<ProviderType, List<Alias>>()
        root.optJSONObject("aliases")?.let { obj ->
            for (type in ProviderType.entries) {
                val arr = obj.optJSONArray(type.name) ?: continue
                aliases[type] = List(arr.length()) { i -> decodeAlias(type, arr.getJSONObject(i)) }
            }
        }
        val accounts = mutableMapOf<ProviderType, AccountInfo>()
        root.optJSONObject("accounts")?.let { obj ->
            for (type in ProviderType.entries) {
                obj.optJSONObject(type.name)?.let { accounts[type] = decodeAccount(it) }
            }
        }
        // Missing in files written before this field existed: then no "last synced" is shown yet.
        val synced = mutableMapOf<ProviderType, Long>()
        root.optJSONObject("synced")?.let { obj ->
            for (type in ProviderType.entries) obj.longOrNull(type.name)?.let { synced[type] = it }
        }
        return Snapshot(aliases, accounts, synced)
    }

    private fun decodeAlias(type: ProviderType, o: JSONObject) = Alias(
        provider = type,
        id = o.getString("id"),
        address = o.getString("address"),
        enabled = o.optBoolean("enabled", true),
        note = o.str("note"),
        createdAtMillis = o.longOrNull("created"),
        forwarded = o.int("fwd"),
        blocked = o.int("blk"),
    )

    private fun decodeAccount(o: JSONObject): AccountInfo {
        val plan = o.optJSONObject("plan")?.let { p ->
            val features = p.optJSONArray("features")
            AccountPlan(
                paid = p.optBoolean("paid", false),
                name = p.str("name").ifBlank { null },
                trial = p.optBoolean("trial", false),
                endsAtMillis = p.longOrNull("ends"),
                sinceMillis = p.longOrNull("since"),
                features = if (features == null) emptyList() else List(features.length()) { features.optString(it) }
                    .mapNotNull { n -> PlanFeature.entries.firstOrNull { it.name == n } },
            )
        }
        val arr = o.optJSONArray("items")
        val items = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
            val o2 = arr.getJSONObject(i)
            val field = AccountField.entries.firstOrNull { f -> f.name == o2.str("f") } ?: return@mapNotNull null
            when (o2.str("t")) {
                "text" -> AccountItem.Text(field, o2.str("value"))
                "count" -> AccountItem.Count(field, o2.optLong("used"), o2.longOrNull("limit"), o2.optBoolean("unlimited"))
                "bytes" -> AccountItem.Bytes(field, o2.optLong("used"), o2.longOrNull("limit"), o2.optBoolean("unlimited"))
                else -> null
            }
        }
        return AccountInfo(plan, items)
    }

    private companion object {
        const val FILE_NAME = "alias_cache.json"

        /** Bump when the layout changes; an older file is then ignored instead of misread. */
        const val VERSION = 1
        val EMPTY = Snapshot(emptyMap(), emptyMap())
    }
}
