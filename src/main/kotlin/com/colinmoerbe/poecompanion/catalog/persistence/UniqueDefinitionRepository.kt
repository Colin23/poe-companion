package com.colinmoerbe.poecompanion.catalog.persistence

import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import org.springframework.stereotype.Repository

/**
 * Persistence boundary for catalog unique definitions.
 *
 * Maps between the catalog domain model and its internal JPA representation.
 */
@Repository
internal class UniqueDefinitionRepository(
    private val jpaRepository: UniqueDefinitionJpaRepository,
) {
    fun save(uniqueDefinition: UniqueDefinition) {
        jpaRepository.save(UniqueDefinitionEntity(uniqueDefinition.id.value))
    }

    fun findById(id: UniqueDefinitionId): UniqueDefinition? =
        jpaRepository
            .findById(id.value)
            .map { entity -> UniqueDefinition(UniqueDefinitionId(entity.id)) }
            .orElse(null)
}
