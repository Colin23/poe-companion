package com.colinmoerbe.poecompanion.build.persistence

import com.colinmoerbe.poecompanion.build.BuildArchetypeId
import com.colinmoerbe.poecompanion.build.BuildVariant
import com.colinmoerbe.poecompanion.build.BuildVariantId
import org.springframework.stereotype.Repository

/**
 * Persists build variants without exposing JPA representations to the domain.
 */
@Repository
internal class BuildVariantPersistenceAdapter(
    private val springDataRepository: SpringDataBuildVariantRepository,
) {
    fun save(buildVariant: BuildVariant) {
        springDataRepository.save(
            BuildVariantEntity(
                id = buildVariant.id.value,
                buildArchetypeId = buildVariant.buildArchetypeId.value,
                label = buildVariant.label,
            ),
        )
    }

    fun findById(id: BuildVariantId): BuildVariant? = springDataRepository
        .findById(id.value)
        .map { entity ->
            BuildVariant(
                id = BuildVariantId(entity.id),
                buildArchetypeId = BuildArchetypeId(entity.buildArchetypeId),
                label = entity.label,
            )
        }.orElse(null)
}
