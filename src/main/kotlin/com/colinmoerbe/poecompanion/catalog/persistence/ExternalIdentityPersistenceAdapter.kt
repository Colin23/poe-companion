package com.colinmoerbe.poecompanion.catalog.persistence

import com.colinmoerbe.poecompanion.catalog.ExternalIdentity
import com.colinmoerbe.poecompanion.catalog.ExternalProvider
import com.colinmoerbe.poecompanion.catalog.ExternalProviderKey
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import org.springframework.stereotype.Repository

/**
 * Persists trusted provider identities without exposing JPA representations to the domain.
 */
@Repository
internal class ExternalIdentityPersistenceAdapter(
    private val springDataRepository: SpringDataExternalIdentityRepository,
) {
    fun save(externalIdentity: ExternalIdentity) {
        springDataRepository.save(
            ExternalIdentityEntity(
                provider = externalIdentity.provider,
                providerKey = externalIdentity.providerKey.value,
                uniqueDefinitionId = externalIdentity.uniqueDefinitionId.value,
            ),
        )
    }

    fun findByProviderAndProviderKey(
        provider: ExternalProvider,
        providerKey: ExternalProviderKey,
    ): ExternalIdentity? = springDataRepository
        .findById(ExternalIdentityEntityId(provider, providerKey.value))
        .map { entity ->
            ExternalIdentity(
                provider = entity.provider,
                providerKey = ExternalProviderKey(entity.providerKey),
                uniqueDefinitionId = UniqueDefinitionId(entity.uniqueDefinitionId),
            )
        }.orElse(null)
}
