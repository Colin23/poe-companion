package com.colinmoerbe.poecompanion.acquisition.probability

import kotlin.math.ceil

/**
 * Fixed independent-attempt probability model.
 *
 * Each attempt has the same success probability and attempts are independent from one another.
 */
data class FixedBernoulliModel(val probabilityPerAttempt: Double) {
    init {
        require(probabilityPerAttempt.isFinite() && probabilityPerAttempt > 0.0 && probabilityPerAttempt <= 1.0) {
            "Probability per attempt must be finite, greater than 0, and at most 1"
        }
    }

    /**
     * Returns the probability of at least one success within the supplied number of attempts.
     */
    fun probabilityOfAtLeastOneSuccessWithin(attempts: Int): Double {
        require(attempts >= 0) { "Attempts must not be negative" }

        return when {
            attempts == 0 -> 0.0

            probabilityPerAttempt == 1.0 -> 1.0

            // Numerically stable equivalent of 1 - (1 - p)^n.
            else -> -Math.expm1(attempts * Math.log1p(-probabilityPerAttempt))
        }
    }

    /**
     * Returns the expected (long-run average) number of attempts until the first success.
     */
    fun expectedAttemptsUntilSuccess(): Double = 1.0 / probabilityPerAttempt

    /**
     * Returns the smallest whole number of attempts whose cumulative success probability reaches the target.
     *
     * A target of 100% is reachable in finitely many attempts only when every attempt succeeds with certainty.
     */
    fun attemptsToReachProbability(targetProbability: Double): Int {
        require(targetProbability.isFinite() && targetProbability > 0.0 && targetProbability <= 1.0) {
            "Target probability must be finite, greater than 0, and at most 1"
        }

        if (probabilityPerAttempt == 1.0) {
            return 1
        }

        require(targetProbability < 1.0) {
            "A 100% target cannot be reached in finitely many attempts when probability per attempt is less than 1"
        }

        // Algebraic solution of 1 - (1 - p)^n >= target, using log1p for stable small probabilities.
        val rawRequiredAttempts =
            Math.log1p(-targetProbability) /
                Math.log1p(-probabilityPerAttempt)
        // Cumulative Double results can round to the target before the mathematical threshold is reached.
        val requiredAttempts = maxOf(1.0, ceil(rawRequiredAttempts))

        require(requiredAttempts <= Int.MAX_VALUE.toDouble()) {
            "Required attempt count exceeds the supported integer range"
        }

        return requiredAttempts.toInt()
    }
}
