package com.colinmoerbe.poecompanion.build.persistence

import com.colinmoerbe.poecompanion.build.BuildArchetype
import com.colinmoerbe.poecompanion.build.BuildArchetypeId
import org.springframework.stereotype.Repository

/**
 * Persists build archetypes without exposing JPA representations to the domain.
 */
@Repository
internal class BuildArchetypePersistenceAdapter(
    private val springDataRepository: SpringDataBuildArchetypeRepository,
) {
    fun save(buildArchetype: BuildArchetype) {
        springDataRepository.save(
            BuildArchetypeEntity(
                id = buildArchetype.id.value,
                name = buildArchetype.name,
            ),
        )
    }

    fun findById(id: BuildArchetypeId): BuildArchetype? = springDataRepository
        .findById(id.value)
        .map { entity ->
            BuildArchetype(
                id = BuildArchetypeId(entity.id),
                name = entity.name,
            )
        }.orElse(null)
}
