package com.colinmoerbe.poecompanion.catalog

/**
 * Opaque provider-owned key identifying one external catalog record.
 *
 * Generic catalog code preserves the key as supplied; provider-specific normalization belongs to the provider adapter.
 */
data class ExternalProviderKey(val value: String) {
    init {
        require(value.isNotBlank()) { "External provider key must not be blank" }
    }
}
