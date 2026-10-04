package com.colinmoerbe.poecompanion.build

import java.util.Collections

/**
 * One logical group of Unique requirements within a build revision.
 */
class RequirementGroup(requirements: List<Requirement>) {
    val requirements: List<Requirement>

    init {
        require(requirements.isNotEmpty()) { "Requirement group must not be empty" }
        require(requirements.map { it.uniqueDefinitionId }.distinct().size == requirements.size) {
            "Requirement group must not contain the same Unique more than once"
        }

        this.requirements = Collections.unmodifiableList(ArrayList(requirements))
    }
}
