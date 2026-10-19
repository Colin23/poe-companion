package com.colinmoerbe.poecompanion.build.identity.persistence

import com.colinmoerbe.poecompanion.build.identity.BuildArchetype
import com.colinmoerbe.poecompanion.build.identity.BuildArchetypeId
import org.springframework.stereotype.Repository

/**
 * Persists build archetypes without exposing JPA representations to the domain.
 */
@Repository
internal class BuildArchetypePersistenceAdapter(private val springDataRepository: SpringDataBuildArchetypeRepository) {
    fun save(buildArchetype: BuildArchetype) {
        springDataRepository.save(
            BuildArchetypeEntity(
                id = buildArchetype.id.value,
                name = buildArchetype.name,
            ),
        )
    }

    fun findAll(): List<BuildArchetype> = springDataRepository.findAll().map { entity ->
        BuildArchetype(BuildArchetypeId(entity.id), entity.name)
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
