package com.colinmoerbe.poecompanion.build

import java.util.Collections

/**
 * Explainable Unique-readiness result for one build revision.
 */
class UniqueReadinessResult internal constructor(
    val readiness: UniqueReadiness,
    groups: List<RequirementGroupEvaluation>,
) {
    val groups: List<RequirementGroupEvaluation> = Collections.unmodifiableList(ArrayList(groups))

    val hasRequiredUniqueGroups: Boolean =
        this.groups.any { it.group.importance != RequirementImportance.UPGRADE }
}
