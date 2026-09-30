package com.colinmoerbe.poecompanion.catalog.persistence

import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import org.springframework.stereotype.Repository

/**
 * Persists catalog unique definitions without exposing JPA representations to the domain.
 */
@Repository
internal class UniqueDefinitionPersistenceAdapter(
    private val springDataRepository: SpringDataUniqueDefinitionRepository,
) {
    fun save(uniqueDefinition: UniqueDefinition) {
        springDataRepository.save(UniqueDefinitionEntity(uniqueDefinition.id.value))
    }

    fun findById(id: UniqueDefinitionId): UniqueDefinition? = springDataRepository
        .findById(id.value)
        .map { entity -> UniqueDefinition(UniqueDefinitionId(entity.id)) }
        .orElse(null)
}
