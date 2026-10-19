package com.colinmoerbe.poecompanion.build.readmodel

import com.colinmoerbe.poecompanion.build.identity.BuildArchetype
import com.colinmoerbe.poecompanion.build.identity.BuildVariant
import com.colinmoerbe.poecompanion.build.identity.BuildVariantId
import com.colinmoerbe.poecompanion.build.identity.persistence.BuildArchetypePersistenceAdapter
import com.colinmoerbe.poecompanion.build.identity.persistence.BuildVariantPersistenceAdapter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Build-owned identity lookup; browser composition never accesses Build JPA repositories. */
@Service
class BuildCatalogQueryService internal constructor(
    private val archetypes: BuildArchetypePersistenceAdapter,
    private val variants: BuildVariantPersistenceAdapter,
) {
    @Transactional(readOnly = true)
    fun findAll(): List<BuildCatalogEntry> {
        val archetypesById = archetypes.findAll().associateBy { it.id }
        return variants.findAll().map { variant ->
            BuildCatalogEntry(
                archetype = requireNotNull(archetypesById[variant.buildArchetypeId]) {
                    "Build variant references an unknown archetype: ${variant.id}"
                },
                variant = variant,
            )
        }
    }

    @Transactional(readOnly = true)
    fun findById(id: BuildVariantId): BuildCatalogEntry? {
        val variant = variants.findById(id) ?: return null
        return BuildCatalogEntry(
            archetype = requireNotNull(archetypes.findById(variant.buildArchetypeId)) {
                "Build variant references an unknown archetype: ${variant.id}"
            },
            variant = variant,
        )
    }
}

data class BuildCatalogEntry(val archetype: BuildArchetype, val variant: BuildVariant) {
    val displayName: String
        get() = if (variant.label == null) archetype.name else "${archetype.name} — ${variant.label}"
}
