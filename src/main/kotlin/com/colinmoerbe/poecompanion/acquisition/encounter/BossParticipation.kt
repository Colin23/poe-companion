package com.colinmoerbe.poecompanion.acquisition.encounter

import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinitionId
import java.util.Collections
import java.util.LinkedHashSet

/**
 * What canonical encounter knowledge says about participating bosses.
 */
sealed interface BossParticipation {

    /**
     * Encounter whose canonical participating bosses are a known fixed set.
     */
    class Fixed(bossDefinitionIds: Set<BossDefinitionId>) : BossParticipation {
        val bossDefinitionIds: Set<BossDefinitionId>

        init {
            require(bossDefinitionIds.isNotEmpty()) { "Fixed boss participation must contain at least one boss" }
            this.bossDefinitionIds = Collections.unmodifiableSet(LinkedHashSet(bossDefinitionIds))
        }

        override fun equals(other: Any?): Boolean =
            this === other || (other is Fixed && bossDefinitionIds == other.bossDefinitionIds)

        override fun hashCode(): Int = bossDefinitionIds.hashCode()
    }

    /**
     * Encounter whose boss roster depends on encounter configuration or player/game state.
     */
    data object ConfigurationDependent : BossParticipation
}
