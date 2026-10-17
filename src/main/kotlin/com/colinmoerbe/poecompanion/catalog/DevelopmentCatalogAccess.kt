package com.colinmoerbe.poecompanion.catalog

import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import org.springframework.stereotype.Component

/** Narrow development-bootstrap entry point; keeps Catalog persistence internal to Catalog. */
@Component
internal class DevelopmentCatalogAccess(private val adapter: UniqueDefinitionPersistenceAdapter) {
    fun save(definition: UniqueDefinition) = adapter.save(definition)

    fun findById(id: UniqueDefinitionId): UniqueDefinition? = adapter.findById(id)
}
