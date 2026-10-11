package com.colinmoerbe.poecompanion.acquisition

import kotlin.math.ceil

/**
 * Fixed independent-attempt probability model.
 *
 * Each attempt has the same success probability and attempts are independent from one another.
 */
data class FixedBernoulliModel(
    val probabilityPerAttempt: Double,
) {
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

        if (attempts == 0) {
            return 0.0
        }
        if (probabilityPerAttempt == 1.0) {
            return 1.0
        }

        // Numerically stable equivalent of 1 - (1 - p)^n.
        return -Math.expm1(attempts * Math.log1p(-probabilityPerAttempt))
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
        val roundedRequiredAttempts = ceil(rawRequiredAttempts)

        require(roundedRequiredAttempts <= Int.MAX_VALUE.toDouble()) {
            "Required attempt count exceeds the supported integer range"
        }

        var attempts = roundedRequiredAttempts.toInt().coerceAtLeast(1)

        // Floating-point rounding can land immediately beside the true integer boundary.
        while (attempts > 1 && probabilityOfAtLeastOneSuccessWithin(attempts - 1) >= targetProbability) {
            attempts--
        }
        while (probabilityOfAtLeastOneSuccessWithin(attempts) < targetProbability) {
            require(attempts < Int.MAX_VALUE) {
                "Required attempt count exceeds the supported integer range"
            }
            attempts++
        }

        return attempts
    }
}
