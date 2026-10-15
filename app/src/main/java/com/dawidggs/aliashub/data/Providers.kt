package com.dawidggs.aliashub.data

import android.content.Context

object Providers {
    fun create(type: ProviderType, config: ProviderConfig, context: Context): AliasProvider =
        when (type) {
            ProviderType.SIMPLELOGIN -> SimpleLoginProvider(config, context)
            ProviderType.ADDY -> AddyProvider(config, context)
            ProviderType.RELAY -> RelayProvider(config)
        }
}