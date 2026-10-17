package com.colinmoerbe.poecompanion.acquisition

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.Normalizer
import java.util.Locale

/**
 * Minimal curator-facing authoring boundary for application-owned boss identities.
 *
 * Similar names require human review rather than becoming canonical identity. This guard prevents ordinary
 * sequential duplicates, not concurrent creations with distinct UUIDs; no name-based database uniqueness is claimed.
 */
@Service
class BossDefinitionService internal constructor(private val persistencePort: BossDefinitionPersistencePort) {
    @Transactional
    fun create(bossDefinition: BossDefinition) {
        val previous = persistencePort.findById(bossDefinition.id)
        if (previous != null) {
            ensureSameDefinition(previous, bossDefinition)
            return
        }

        val candidateKey = normalizedName(bossDefinition.displayName)
        val collision =
            persistencePort.findAll().firstOrNull { normalizedName(it.displayName) == candidateKey }
        if (collision != null) {
            throw BossDefinitionCreationConflictException(
                "Likely duplicate boss name; review the existing boss identity: ${collision.id}",
            )
        }

        if (!persistencePort.insertIfAbsent(bossDefinition)) {
            val concurrent =
                requireNotNull(persistencePort.findById(bossDefinition.id)) {
                    "Boss definition disappeared after a concurrent identity insertion"
                }
            ensureSameDefinition(concurrent, bossDefinition)
        }
    }

    @Transactional(readOnly = true)
    fun findById(id: BossDefinitionId): BossDefinition? = persistencePort.findById(id)

    private fun ensureSameDefinition(existing: BossDefinition, requested: BossDefinition) {
        if (existing.displayName != requested.displayName) {
            throw BossDefinitionCreationConflictException(
                "Existing BossDefinitionId cannot be redefined with a different display name",
            )
        }
    }

    private fun normalizedName(displayName: String): String =
        Normalizer.normalize(displayName, Normalizer.Form.NFKC)
            .trim()
            .replace(WHITESPACE, " ")
            .lowercase(Locale.ROOT)

    private companion object {
        val WHITESPACE = Regex("(?U)\\s+")
    }
}
