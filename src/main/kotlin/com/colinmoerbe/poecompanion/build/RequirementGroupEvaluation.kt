package com.colinmoerbe.poecompanion.build

import java.util.Collections

/**
 * Explainable evaluation of one requirement group.
 */
class RequirementGroupEvaluation internal constructor(
    val group: RequirementGroup,
    requirements: List<RequirementEvaluation>,
) {
    val requirements: List<RequirementEvaluation> = Collections.unmodifiableList(ArrayList(requirements))

    val satisfied: Boolean =
        when (group.logic) {
            RequirementLogic.ALL -> this.requirements.all { it.satisfied }
            RequirementLogic.ANY -> this.requirements.any { it.satisfied }
        }
}
