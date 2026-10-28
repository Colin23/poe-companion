package com.colinmoerbe.poecompanion.acquisition.overrides

import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GamePatch
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.Ruleset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DropRateOverrideKeyTests {
    private val accountContextId = AccountContextId.generate()
    private val dropRelationshipId = DropRelationshipId.generate()
    private val leagueDefinitionId = LeagueDefinitionId.generate()
    private val version = CompatibilityVersion(3, 30)

    private fun context(
        compatibilityVersion: CompatibilityVersion = version,
        ruleset: Ruleset = Ruleset.NORMAL,
        gamePatch: GamePatch? = GamePatch(compatibilityVersion, 1),
        leagueDefinitionId: LeagueDefinitionId = this.leagueDefinitionId,
    ): EvaluationContext = EvaluationContext(compatibilityVersion, ruleset, gamePatch, leagueDefinitionId)

    private fun key(context: EvaluationContext = context()): DropRateOverrideKey =
        DropRateOverrideKey(accountContextId, dropRelationshipId, context)

    @Test
    fun `identical account relationship and evaluation context should have equal keys`() {
        assertThat(key()).isEqualTo(key())
    }

    @Test
    fun `different account or relationship should have different keys`() {
        val original = key()

        assertThat(original).isNotEqualTo(original.copy(accountContextId = AccountContextId.generate()))
        assertThat(original).isNotEqualTo(original.copy(dropRelationshipId = DropRelationshipId.generate()))
    }

    @Test
    fun `different league or ruleset should not match even with the same exact patch`() {
        val original = key()

        assertThat(original).isNotEqualTo(key(context(leagueDefinitionId = LeagueDefinitionId.generate())))
        assertThat(original).isNotEqualTo(key(context(ruleset = Ruleset.RUTHLESS)))
    }

    @Test
    fun `version patch and patch suffix are exact matching dimensions`() {
        val original = key()

        assertThat(original).isNotEqualTo(key(context(compatibilityVersion = CompatibilityVersion(3, 31))))
        assertThat(original).isNotEqualTo(key(context(gamePatch = GamePatch(version, 2))))
        assertThat(original).isNotEqualTo(key(context(gamePatch = GamePatch(version, 1, "a"))))
    }

    @Test
    fun `unspecified patch is its own exact confirmation context rather than a wildcard`() {
        val unspecified = key(context(gamePatch = null))
        val patchOne = key(context(gamePatch = GamePatch(version, 1)))
        val patchTwo = key(context(gamePatch = GamePatch(version, 2)))

        assertThat(unspecified).isEqualTo(key(context(gamePatch = null)))
        assertThat(unspecified).isNotEqualTo(patchOne)
        assertThat(unspecified).isNotEqualTo(patchTwo)
        assertThat(patchOne).isNotEqualTo(patchTwo)
    }

    @Test
    fun `personal override key requires an explicit league identity`() {
        assertThatThrownBy {
            DropRateOverrideKey(
                accountContextId,
                dropRelationshipId,
                EvaluationContext(version, Ruleset.NORMAL, gamePatch = GamePatch(version, 1)),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `personal override key inherits patch and compatibility version consistency`() {
        assertThatThrownBy {
            key(context(gamePatch = GamePatch(CompatibilityVersion(3, 31), 1)))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
