package com.colinmoerbe.poecompanion.browser.environment

import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.LeagueType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GameEnvironmentFormParserTests {
    private fun validInput() = GameEnvironmentFormInput(
        version = "3.30",
        type = "CHALLENGE",
        mortality = "SOFTCORE",
        participation = "SSF",
        ruleset = "NORMAL",
    )

    @Test
    fun `valid dimensions parse to a typed domain choice`() {
        val choice = GameEnvironmentFormParser.parse(validInput())
        assertThat(choice).isNotNull
        assertThat(choice?.compatibilityVersion).isEqualTo(CompatibilityVersion(3, 30))
        assertThat(choice?.type).isEqualTo(LeagueType.CHALLENGE)
        assertThat(choice?.leagueDefinitionId).isNull()
    }

    @Test
    fun `malformed versions and overflow cannot throw`() {
        listOf("", "3", "3.30.2", "3.-1", "3.foo", "999999999999999999.30").forEach { version ->
            assertThat(GameEnvironmentFormParser.parse(validInput().copy(version = version))).isNull()
        }
    }

    @Test
    fun `unknown enums and invalid IDs are rejected as malformed HTTP input`() {
        assertThat(GameEnvironmentFormParser.parse(validInput().copy(type = "UNKNOWN"))).isNull()
        assertThat(GameEnvironmentFormParser.parse(validInput().copy(leagueId = "not-a-uuid"))).isNull()
    }
}
