package com.colinmoerbe.poecompanion.acquisition.probability

import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttempts
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class FixedProbabilityCalculationTests {
    private val sourceId = AcquisitionSourceId.generate()
    private val relationship =
        DropRelationship(DropRelationshipId.generate(), sourceId, UniqueDefinitionId.generate())

    @Test
    fun `certain success reaches every canonical target in one attempt`() {
        val result = FixedProbabilityCalculation.from(relationship, FixedBernoulliModel(1.0), null)

        assertThat(result.expectedAttemptsUntilSuccess).isEqualTo(1.0)
        assertThat(result.attemptsFor50Percent).isEqualTo(1)
        assertThat(result.attemptsFor75Percent).isEqualTo(1)
        assertThat(result.attemptsFor90Percent).isEqualTo(1)
        assertThat(result.attemptsFor95Percent).isEqualTo(1)
        assertThat(result.attemptsFor99Percent).isEqualTo(1)
        assertThat(result.probabilityWithinAvailableAttempts).isNull()
    }

    @Test
    fun `recorded zero attempts produces zero chance even at certain drop probability`() {
        val available = AvailableAttempts(AccountContextId.generate(), sourceId, 0)

        val result = FixedProbabilityCalculation.from(relationship, FixedBernoulliModel(1.0), available)

        assertThat(result.probabilityWithinAvailableAttempts).isEqualTo(0.0)
    }

    @Test
    fun `very small positive probability must not invent finite expected or threshold attempt counts`() {
        val result = FixedProbabilityCalculation.from(relationship, FixedBernoulliModel(Double.MIN_VALUE), null)

        assertThat(result.expectedAttemptsUntilSuccess).isNull()
        assertThat(result.attemptsFor50Percent).isNull()
        assertThat(result.attemptsFor75Percent).isNull()
        assertThat(result.attemptsFor90Percent).isNull()
        assertThat(result.attemptsFor95Percent).isNull()
        assertThat(result.attemptsFor99Percent).isNull()
        assertThat(result.probabilityWithinAvailableAttempts).isNull()
    }
}
