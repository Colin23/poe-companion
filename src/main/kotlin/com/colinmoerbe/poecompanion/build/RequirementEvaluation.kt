package com.colinmoerbe.poecompanion.build

/**
 * Evaluation of one requirement against current ownership.
 */
data class RequirementEvaluation(
    val requirement: Requirement,
    val ownedQuantity: Int,
) {
    init {
        require(ownedQuantity >= 0) { "Owned quantity must not be negative" }
    }

    val satisfied: Boolean
        get() = ownedQuantity >= requirement.requiredQuantity
}
