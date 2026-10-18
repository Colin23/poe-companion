package com.colinmoerbe.poecompanion.catalog

import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Catalog-owned read API for stable Unique identities. Metadata and ownership are independent concerns.
 */
@Service
class UniqueCatalogQueryService internal constructor(private val adapter: UniqueDefinitionPersistenceAdapter) {
    @Transactional(readOnly = true)
    fun findAll(): List<UniqueDefinition> = adapter.findAll()

    @Transactional(readOnly = true)
    fun findById(id: UniqueDefinitionId): UniqueDefinition? = adapter.findById(id)
}
