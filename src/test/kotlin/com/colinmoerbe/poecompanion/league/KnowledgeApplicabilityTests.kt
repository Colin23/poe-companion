package com.colinmoerbe.poecompanion.league

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Verifies strict, dimension-aware evaluation-context applicability.
 */
class KnowledgeApplicabilityTests {

    private val version = CompatibilityVersion(3, 30)
    private val otherVersion = CompatibilityVersion(3, 31)
    private val leagueId = LeagueDefinitionId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val otherLeagueId = LeagueDefinitionId(UUID.fromString("00000000-0000-0000-0000-000000000002"))

    @Test
    fun `confirmed broad patch and league dimensions should not restrict a detailed context`() {
        val applicability =
            KnowledgeApplicability(
                compatibilityVersion = version,
                ruleset = Ruleset.NORMAL,
            )
        val context =
            EvaluationContext(
                compatibilityVersion = version,
                ruleset = Ruleset.NORMAL,
                gamePatch = GamePatch(version, 1),
                leagueDefinitionId = leagueId,
            )

        assertThat(applicability.evaluateAgainst(context)).isEqualTo(ApplicabilityResult.APPLIES)
    }

    @Test
    fun `different compatibility version should not apply`() {
        val applicability = KnowledgeApplicability(compatibilityVersion = version)
        val context = EvaluationContext(otherVersion, Ruleset.NORMAL)

        assertThat(applicability.evaluateAgainst(context)).isEqualTo(ApplicabilityResult.DOES_NOT_APPLY)
    }

    @Test
    fun `different ruleset should not apply`() {
        val applicability = KnowledgeApplicability(ruleset = Ruleset.RUTHLESS)
        val context = EvaluationContext(version, Ruleset.NORMAL)

        assertThat(applicability.evaluateAgainst(context)).isEqualTo(ApplicabilityResult.DOES_NOT_APPLY)
    }

    @Test
    fun `matching exact patch should apply`() {
        val patch = GamePatch(version, 1)
        val applicability =
            KnowledgeApplicability(
                compatibilityVersion = version,
                gamePatch = patch,
            )
        val context = EvaluationContext(version, Ruleset.NORMAL, gamePatch = patch)

        assertThat(applicability.evaluateAgainst(context)).isEqualTo(ApplicabilityResult.APPLIES)
    }

    @Test
    fun `patch-specific knowledge should be unknown when context lacks exact patch`() {
        val applicability =
            KnowledgeApplicability(
                compatibilityVersion = version,
                gamePatch = GamePatch(version, 1),
            )
        val context = EvaluationContext(version, Ruleset.NORMAL)

        assertThat(applicability.evaluateAgainst(context)).isEqualTo(ApplicabilityResult.UNKNOWN)
    }

    @Test
    fun `different exact patch should not apply`() {
        val applicability =
            KnowledgeApplicability(
                compatibilityVersion = version,
                gamePatch = GamePatch(version, 1),
            )
        val context = EvaluationContext(version, Ruleset.NORMAL, gamePatch = GamePatch(version, 2))

        assertThat(applicability.evaluateAgainst(context)).isEqualTo(ApplicabilityResult.DOES_NOT_APPLY)
    }

    @Test
    fun `matching league should apply`() {
        val applicability = KnowledgeApplicability(leagueDefinitionId = leagueId)
        val context = EvaluationContext(version, Ruleset.NORMAL, leagueDefinitionId = leagueId)

        assertThat(applicability.evaluateAgainst(context)).isEqualTo(ApplicabilityResult.APPLIES)
    }

    @Test
    fun `missing required league precision should be unknown`() {
        val applicability = KnowledgeApplicability(leagueDefinitionId = leagueId)
        val context = EvaluationContext(version, Ruleset.NORMAL)

        assertThat(applicability.evaluateAgainst(context)).isEqualTo(ApplicabilityResult.UNKNOWN)
    }

    @Test
    fun `different league should not apply`() {
        val applicability = KnowledgeApplicability(leagueDefinitionId = leagueId)
        val context = EvaluationContext(version, Ruleset.NORMAL, leagueDefinitionId = otherLeagueId)

        assertThat(applicability.evaluateAgainst(context)).isEqualTo(ApplicabilityResult.DOES_NOT_APPLY)
    }

    @Test
    fun `known mismatch should win over missing precision`() {
        val applicability =
            KnowledgeApplicability(
                compatibilityVersion = version,
                ruleset = Ruleset.RUTHLESS,
                gamePatch = GamePatch(version, 1),
            )
        val context = EvaluationContext(version, Ruleset.NORMAL)

        assertThat(applicability.evaluateAgainst(context)).isEqualTo(ApplicabilityResult.DOES_NOT_APPLY)
    }

    @Test
    fun `patch constraint without compatibility version should be rejected`() {
        assertThatThrownBy {
            KnowledgeApplicability(gamePatch = GamePatch(version, 1))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `patch constraint from another compatibility version should be rejected`() {
        assertThatThrownBy {
            KnowledgeApplicability(
                compatibilityVersion = version,
                gamePatch = GamePatch(otherVersion, 1),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
