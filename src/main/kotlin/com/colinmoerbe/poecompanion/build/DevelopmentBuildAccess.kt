package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.build.persistence.BuildArchetypePersistenceAdapter
import com.colinmoerbe.poecompanion.build.persistence.BuildVariantPersistenceAdapter
import com.colinmoerbe.poecompanion.build.persistence.BuildVariantRevisionPersistenceAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/** Narrow development-bootstrap entry point; build draft persistence remains owned by the Build module.
 *
 * Property-gated without @Profile("dev") so integration tests can opt in and initialize explicitly.
 */
@Component
@ConditionalOnProperty(
    prefix = "poe-companion.dev-fixture",
    name = ["enabled"],
    havingValue = "true",
)
internal class DevelopmentBuildAccess(
    private val archetypes: BuildArchetypePersistenceAdapter,
    private val variants: BuildVariantPersistenceAdapter,
    private val revisions: BuildVariantRevisionPersistenceAdapter,
    private val activation: BuildVariantRevisionActivationService,
) {
    fun findArchetypeById(id: BuildArchetypeId): BuildArchetype? = archetypes.findById(id)

    fun findVariantById(id: BuildVariantId): BuildVariant? = variants.findById(id)

    fun findRevisionById(id: BuildVariantRevisionId): BuildVariantRevision? = revisions.findById(id)

    fun createAndActivate(archetype: BuildArchetype, variant: BuildVariant, revision: BuildVariantRevision) {
        archetypes.save(archetype)
        variants.save(variant)
        revisions.saveDraftSnapshot(revision)
        activation.activate(revision.id)
    }
}
