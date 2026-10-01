package com.colinmoerbe.poecompanion.league

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Verifies basic league-definition invariants.
 */
class LeagueDefinitionTests {

    @Test
    fun `blank league name should be rejected`() {
        assertThatThrownBy {
            LeagueDefinition(
                id = LeagueDefinitionId.generate(),
                name = " ",
                category = LeagueCategory.CHALLENGE,
                participation = LeagueParticipation.SSF,
                mortality = LeagueMortality.SOFTCORE,
                realm = GameRealm.PC,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
