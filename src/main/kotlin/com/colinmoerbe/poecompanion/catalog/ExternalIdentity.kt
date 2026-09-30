package com.colinmoerbe.poecompanion.catalog

/**
 * Trusted mapping from a provider-owned identity to one application-owned unique definition.
 */
data class ExternalIdentity(
    val provider: ExternalProvider,
    val providerKey: ExternalProviderKey,
    val uniqueDefinitionId: UniqueDefinitionId,
)
