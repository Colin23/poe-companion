package com.colinmoerbe.poecompanion.acquisition

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class FixedBernoulliModelTests {

    @Test
    fun `probability per attempt should be greater than zero and at most one`() {
        assertThat(FixedBernoulliModel(0.10).probabilityPerAttempt).isEqualTo(0.10)
        assertThat(FixedBernoulliModel(1.0).probabilityPerAttempt).isEqualTo(1.0)

        listOf(0.0, -0.1, 1.0001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertThatThrownBy { FixedBernoulliModel(it) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `zero attempts should have zero cumulative success probability`() {
        val model = FixedBernoulliModel(0.10)

        assertThat(model.probabilityOfAtLeastOneSuccessWithin(0)).isEqualTo(0.0)
    }

    @Test
    fun `one attempt should have the per-attempt success probability`() {
        val model = FixedBernoulliModel(0.10)

        assertThat(model.probabilityOfAtLeastOneSuccessWithin(1))
            .isCloseTo(0.10, within(1e-12))
    }

    @Test
    fun `twelve ten-percent attempts should have about seventy-one point eight percent cumulative probability`() {
        val model = FixedBernoulliModel(0.10)

        assertThat(model.probabilityOfAtLeastOneSuccessWithin(12))
            .isCloseTo(0.717570463519, within(1e-12))
    }

    @Test
    fun `certain success should have one hundred percent cumulative probability after any positive attempt count`() {
        val model = FixedBernoulliModel(1.0)

        assertThat(model.probabilityOfAtLeastOneSuccessWithin(1)).isEqualTo(1.0)
        assertThat(model.probabilityOfAtLeastOneSuccessWithin(100)).isEqualTo(1.0)
    }

    @Test
    fun `negative attempt count should be rejected`() {
        val model = FixedBernoulliModel(0.10)

        assertThatThrownBy { model.probabilityOfAtLeastOneSuccessWithin(-1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `expected attempts should be the long-run average until first success`() {
        assertThat(FixedBernoulliModel(0.10).expectedAttemptsUntilSuccess())
            .isCloseTo(10.0, within(1e-12))
        assertThat(FixedBernoulliModel(1.0).expectedAttemptsUntilSuccess()).isEqualTo(1.0)
    }

    @Test
    fun `ten-percent model should require twenty-nine attempts to reach ninety-five percent`() {
        val model = FixedBernoulliModel(0.10)

        assertThat(model.attemptsToReachProbability(0.95)).isEqualTo(29)
        assertThat(model.probabilityOfAtLeastOneSuccessWithin(28)).isLessThan(0.95)
        assertThat(model.probabilityOfAtLeastOneSuccessWithin(29)).isGreaterThanOrEqualTo(0.95)
    }

    @Test
    fun `threshold calculation should return the first attempt count that reaches arbitrary targets`() {
        val model = FixedBernoulliModel(0.10)

        assertThat(model.attemptsToReachProbability(0.50)).isEqualTo(7)
        assertThat(model.attemptsToReachProbability(0.99)).isEqualTo(44)
    }

    @Test
    fun `near certain target should not round cumulative probability into an earlier success threshold`() {
        val model = FixedBernoulliModel(1e-7)
        val target = 0.9999999999999999

        assertThat(model.attemptsToReachProbability(target)).isEqualTo(367_367_988)
    }

    @Test
    fun `positive target should require at least one attempt even when the threshold quotient underflows`() {
        val model = FixedBernoulliModel(0.99)

        assertThat(model.attemptsToReachProbability(Double.MIN_VALUE)).isEqualTo(1)
    }

    @Test
    fun `certain success should reach every positive target in one attempt`() {
        val model = FixedBernoulliModel(1.0)

        assertThat(model.attemptsToReachProbability(0.50)).isEqualTo(1)
        assertThat(model.attemptsToReachProbability(1.0)).isEqualTo(1)
    }

    @Test
    fun `one hundred percent target should be rejected when success is not certain`() {
        val model = FixedBernoulliModel(0.10)

        assertThatThrownBy { model.attemptsToReachProbability(1.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `threshold target should be greater than zero and at most one`() {
        val model = FixedBernoulliModel(0.10)

        listOf(0.0, -0.1, 1.0001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertThatThrownBy { model.attemptsToReachProbability(it) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `threshold should reject attempt counts beyond supported integer range`() {
        val model = FixedBernoulliModel(1e-12)

        assertThatThrownBy { model.attemptsToReachProbability(0.95) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("integer range")
    }
}
