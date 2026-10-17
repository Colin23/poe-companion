package com.colinmoerbe.poecompanion.acquisition.persistence

import com.colinmoerbe.poecompanion.acquisition.BossDefinition
import com.colinmoerbe.poecompanion.acquisition.BossDefinitionId
import com.colinmoerbe.poecompanion.acquisition.BossDefinitionPersistencePort
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Maps canonical boss identity to PostgreSQL without exposing JPA objects outside the persistence adapter.
 */
@Repository
internal class BossDefinitionPersistenceAdapter(private val repository: SpringDataBossDefinitionRepository) :
    BossDefinitionPersistencePort {
    @Transactional
    override fun insertIfAbsent(bossDefinition: BossDefinition): Boolean =
        repository.insertIfAbsent(bossDefinition.id.value, bossDefinition.displayName) == 1

    @Transactional(readOnly = true)
    override fun findById(id: BossDefinitionId): BossDefinition? =
        repository.findById(id.value).map { toDomain(it) }.orElse(null)

    @Transactional(readOnly = true)
    override fun findAll(): List<BossDefinition> = repository.findAll().map { toDomain(it) }

    private fun toDomain(entity: BossDefinitionEntity): BossDefinition =
        BossDefinition(BossDefinitionId(entity.id), entity.displayName)
}
