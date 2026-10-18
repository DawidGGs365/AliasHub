package com.dawidggs.aliashub.data

import android.content.Context

object Providers {
    fun create(type: ProviderType, config: ProviderConfig, context: Context): AliasProvider =
        when (type) {
            ProviderType.SIMPLELOGIN -> SimpleLoginProvider(config, context)
            ProviderType.ADDY -> AddyProvider(config, context)
            ProviderType.RELAY -> RelayProvider(config)
        }

    /**
     * The provider's web dashboard, where plans and account settings are managed. Self-hosted
     * SimpleLogin and Addy instances use their own [baseUrl]; Relay has no self-hosted option.
     */
    fun dashboardUrl(type: ProviderType, baseUrl: String?): String = when (type) {
        ProviderType.SIMPLELOGIN -> Http.baseUrl(baseUrl, "https://app.simplelogin.io") + "/dashboard/"
        ProviderType.ADDY -> Http.baseUrl(baseUrl, "https://app.addy.io") + "/"
        ProviderType.RELAY -> "https://relay.firefox.com/accounts/profile/"
    }
}
