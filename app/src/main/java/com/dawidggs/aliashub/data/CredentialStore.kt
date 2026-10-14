@file:Suppress("DEPRECATION")

package com.dawidggs.aliashub.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences.create
import androidx.security.crypto.MasterKey

/** API keys live only here: AES-256 encrypted, keyed by an Android Keystore master key. */
class CredentialStore(context: Context) {
    private val prefs = create(
        context,
        "aliashub_credentials",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun get(type: ProviderType): ProviderConfig? {
        val token = prefs.getString("${type.name}.token", null) ?: return null
        return ProviderConfig(
            token = token,
            baseUrl = prefs.getString("${type.name}.baseUrl", null),
        )
    }

    fun save(type: ProviderType, config: ProviderConfig) {
        prefs.edit()
            .putString("${type.name}.token", config.token)
            .putString("${type.name}.baseUrl", config.baseUrl)
            .apply()
    }

    fun clear(type: ProviderType) {
        prefs.edit()
            .remove("${type.name}.token")
            .remove("${type.name}.baseUrl")
            .apply()
    }

    fun configuredTypes(): List<ProviderType> = ProviderType.entries.filter { get(it) != null }
}
