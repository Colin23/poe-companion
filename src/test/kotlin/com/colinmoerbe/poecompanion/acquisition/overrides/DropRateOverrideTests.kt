package com.colinmoerbe.poecompanion.acquisition.overrides

import com.colinmoerbe.poecompanion.acquisition.probability.FixedBernoulliModel
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

class DropRateOverrideTests {
    private val version = CompatibilityVersion(3, 30)
    private val key = DropRateOverrideKey(
        accountContextId = AccountContextId.generate(),
        dropRelationshipId = DropRelationshipId.generate(),
        evaluationContext = EvaluationContext(
            compatibilityVersion = version,
            ruleset = Ruleset.NORMAL,
            gamePatch = GamePatch(version, 1),
            leagueDefinitionId = LeagueDefinitionId.generate(),
        ),
    )

    @Test
    fun `confirmed override retains its revision key and validated fixed probability`() {
        val revisionId = DropRateOverrideRevisionId.generate()
        val personal = DropRateOverride(revisionId, key, FixedBernoulliModel(0.13))

        assertThat(personal.revisionId).isEqualTo(revisionId)
        assertThat(personal.key).isEqualTo(key)
        assertThat(personal.model.probabilityPerAttempt).isEqualTo(0.13)
    }

    @Test
    fun `invalid personal probabilities are rejected by the existing fixed model`() {
        listOf(0.0, -0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { invalid ->
            assertThatThrownBy {
                DropRateOverride(DropRateOverrideRevisionId.generate(), key, FixedBernoulliModel(invalid))
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `a revised confirmation retains its slot but not the source revision identity or value`() {
        val original = DropRateOverride(DropRateOverrideRevisionId.generate(), key, FixedBernoulliModel(0.13))
        val revised = DropRateOverride(DropRateOverrideRevisionId.generate(), key, FixedBernoulliModel(0.11))

        assertThat(revised.key).isEqualTo(original.key)
        assertThat(revised.revisionId).isNotEqualTo(original.revisionId)
        assertThat(original.model.probabilityPerAttempt).isEqualTo(0.13)
        assertThat(revised.model.probabilityPerAttempt).isEqualTo(0.11)
    }
}
