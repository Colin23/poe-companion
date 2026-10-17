package com.colinmoerbe.poecompanion.acquisition

/**
 * Persistence boundary for stable boss identities. Human-facing names never become identity keys.
 */
internal interface BossDefinitionPersistencePort {
    fun findById(id: BossDefinitionId): BossDefinition?

    /** The V0.1 curator corpus is small; this collection supports a conservative name-collision guard. */
    fun findAll(): List<BossDefinition>

    /** Atomically inserts an absent ID, returning false if that ID already exists. */
    fun insertIfAbsent(bossDefinition: BossDefinition): Boolean
}
