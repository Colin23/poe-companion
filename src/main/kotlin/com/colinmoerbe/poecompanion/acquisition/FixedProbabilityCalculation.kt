package com.colinmoerbe.poecompanion.acquisition

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
                attemptsFor50Percent = attemptsOrNull(model, 0.50),
                attemptsFor75Percent = attemptsOrNull(model, 0.75),
                attemptsFor90Percent = attemptsOrNull(model, 0.90),
                attemptsFor95Percent = attemptsOrNull(model, 0.95),
                attemptsFor99Percent = attemptsOrNull(model, 0.99),
                probabilityWithinAvailableAttempts =
                availableAttempts?.let { model.probabilityOfAtLeastOneSuccessWithin(it.count) },
            )
        }

        private fun attemptsOrNull(model: FixedBernoulliModel, targetProbability: Double): Int? =
            try {
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
