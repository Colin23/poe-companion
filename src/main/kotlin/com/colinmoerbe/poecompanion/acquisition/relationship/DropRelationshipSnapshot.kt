package com.colinmoerbe.poecompanion.acquisition.relationship

import com.colinmoerbe.poecompanion.league.GamePatch

/**
 * Complete canonical drop-relationship state that becomes current at one game-patch change point.
 *
 * A null effective patch is the baseline for the timeline's compatibility version.
 */
data class DropRelationshipSnapshot(val effectiveFromPatch: GamePatch?, val state: DropRelationshipState)
