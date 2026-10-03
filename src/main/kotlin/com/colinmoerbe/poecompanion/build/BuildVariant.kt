package com.colinmoerbe.poecompanion.build

/**
 * Materially different form of a build archetype from the Companion's tracked-requirement perspective.
 *
 * [label] is optional because an archetype with only one relevant variant does not need an artificial display
 * qualifier.
 */
class BuildVariant(val id: BuildVariantId, val buildArchetypeId: BuildArchetypeId, val label: String? = null) {
    init {
        require(label == null || label.isNotBlank()) { "Build variant label must not be blank" }
    }
}
