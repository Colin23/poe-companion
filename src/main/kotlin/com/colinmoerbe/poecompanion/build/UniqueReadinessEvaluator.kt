package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.account.CurrentOwnership

/**
 * Pure evaluator for Unique readiness of an active build revision.
 */
class UniqueReadinessEvaluator {

    fun evaluate(revision: BuildVariantRevision, ownership: CurrentOwnership): UniqueReadinessResult {
        check(revision.status == BuildVariantRevisionStatus.ACTIVE) {
            "Only active build revisions can be evaluated for Unique readiness"
        }

        val groups = revision.requirementGroups.map { evaluateGroup(it, ownership) }
        val readiness =
            when {
                groups.any { it.isUnsatisfied(RequirementImportance.ENABLING) } -> UniqueReadiness.BLOCKED
                groups.any { it.isUnsatisfied(RequirementImportance.CORE) } -> UniqueReadiness.PARTIAL
                else -> UniqueReadiness.UNIQUE_READY
            }

        return UniqueReadinessResult(readiness, groups)
    }

    private fun evaluateGroup(group: RequirementGroup, ownership: CurrentOwnership): RequirementGroupEvaluation {
        val requirements =
            group.requirements.map { requirement ->
                RequirementEvaluation(
                    requirement = requirement,
                    ownedQuantity = ownership.quantityOf(requirement.uniqueDefinitionId),
                )
            }

        return RequirementGroupEvaluation(group, requirements)
    }

    private fun RequirementGroupEvaluation.isUnsatisfied(importance: RequirementImportance): Boolean =
        group.importance == importance && !satisfied
}
