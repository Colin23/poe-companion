package com.colinmoerbe.poecompanion.acquisition.probability

import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttempts
import com.colinmoerbe.poecompanion.acquisition.attempts.CompletedBossEncounterAttempt
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship

/**
 * Supported fixed-Bernoulli farming outlook for one currently applicable source-to-Unique relationship.
 *
 * A null [probabilityWithinAvailableAttempts] means attempts were not recorded, not that their count is zero.
 * A null expected count means the Double result is not finite. A null threshold count means it would exceed
 * Int.MAX_VALUE attempts; these unsupported values must never be represented as fabricated finite numbers.
 */
data class FixedProbabilityCalculation(
    val attemptDefinition: CompletedBossEncounterAttempt,
    val expectedAttemptsUntilSuccess: Double?,
    val attemptsFor50Percent: Int?,
    val attemptsFor75Percent: Int?,
    val attemptsFor90Percent: Int?,
    val attemptsFor95Percent: Int?,
    val attemptsFor99Percent: Int?,
    val probabilityWithinAvailableAttempts: Double?,
) {
    companion object {
        private const val FIFTY_PERCENT = 0.50
        private const val SEVENTY_FIVE_PERCENT = 0.75
        private const val NINETY_PERCENT = 0.90
        private const val NINETY_FIVE_PERCENT = 0.95
        private const val NINETY_NINE_PERCENT = 0.99

        /** Calculates from accepted knowledge; the caller checks source and relationship eligibility. */
        internal fun from(
            relationship: DropRelationship,
            model: FixedBernoulliModel,
            availableAttempts: AvailableAttempts?,
        ): FixedProbabilityCalculation {
            val expected = model.expectedAttemptsUntilSuccess()
            return FixedProbabilityCalculation(
                attemptDefinition = CompletedBossEncounterAttempt(relationship.acquisitionSourceId),
                expectedAttemptsUntilSuccess = expected.takeIf { it.isFinite() },
                attemptsFor50Percent = attemptsOrNull(model, FIFTY_PERCENT),
                attemptsFor75Percent = attemptsOrNull(model, SEVENTY_FIVE_PERCENT),
                attemptsFor90Percent = attemptsOrNull(model, NINETY_PERCENT),
                attemptsFor95Percent = attemptsOrNull(model, NINETY_FIVE_PERCENT),
                attemptsFor99Percent = attemptsOrNull(model, NINETY_NINE_PERCENT),
                probabilityWithinAvailableAttempts =
                availableAttempts?.let { model.probabilityOfAtLeastOneSuccessWithin(it.count) },
            )
        }

        private fun attemptsOrNull(model: FixedBernoulliModel, targetProbability: Double): Int? = try {
            model.attemptsToReachProbability(targetProbability)
        } catch (exception: IllegalArgumentException) {
            // Both inputs are validated and these fixed targets are < 100%; only Int-range overflow is expected.
            if (exception.message != "Required attempt count exceeds the supported integer range") {
                throw exception
            }
            null
        }
    }
}
