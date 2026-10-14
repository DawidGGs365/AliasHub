package com.dawidggs.aliashub.data

object Providers {
    fun create(type: ProviderType, config: ProviderConfig): AliasProvider = when (type) {
        ProviderType.SIMPLELOGIN -> SimpleLoginProvider(config)
        ProviderType.ADDY -> AddyProvider(config)
        ProviderType.RELAY -> RelayProvider(config)
    }
}
