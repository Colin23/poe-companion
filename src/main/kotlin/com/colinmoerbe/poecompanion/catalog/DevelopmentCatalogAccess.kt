package com.colinmoerbe.poecompanion.catalog

import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Narrow development-bootstrap entry point; keeps Catalog persistence internal to Catalog.
 *
 * Property-gated without @Profile("dev") so integration tests can opt in and initialize explicitly.
 */
@Component
@ConditionalOnProperty(
    prefix = "poe-companion.dev-fixture",
    name = ["enabled"],
    havingValue = "true",
)
internal class DevelopmentCatalogAccess(private val adapter: UniqueDefinitionPersistenceAdapter) {
    fun save(definition: UniqueDefinition) = adapter.save(definition)

    fun findById(id: UniqueDefinitionId): UniqueDefinition? = adapter.findById(id)
}
