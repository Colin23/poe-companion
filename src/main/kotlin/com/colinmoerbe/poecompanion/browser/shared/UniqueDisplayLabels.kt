package com.colinmoerbe.poecompanion.browser.shared

import com.colinmoerbe.poecompanion.DevelopmentFixtureIds
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId

/** Temporary browser-only label policy; replace with Catalog metadata in Milestone 7. */
internal object UniqueDisplayLabels {
    fun forId(id: UniqueDefinitionId): UniqueDisplayLabel {
        val fixtureLabel = DevelopmentFixtureIds.uniqueLabels[id]
        return UniqueDisplayLabel(
            name = fixtureLabel ?: "Unique (name unavailable)",
            synthetic = fixtureLabel != null,
        )
    }
}

internal data class UniqueDisplayLabel(val name: String, val synthetic: Boolean)
