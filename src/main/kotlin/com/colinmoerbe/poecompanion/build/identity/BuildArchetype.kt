package com.colinmoerbe.poecompanion.build.identity

/**
 * Human-level build family whose identity remains stable across game-version revisions.
 */
class BuildArchetype(val id: BuildArchetypeId, val name: String) {
    init {
        require(name.isNotBlank()) { "Build archetype name must not be blank" }
    }
}
