package com.colinmoerbe.poecompanion.acquisition.behavior

import com.colinmoerbe.poecompanion.league.GamePatch

/** An accepted complete probability-behavior fact effective from one game-patch change point. */
data class DropProbabilityBehaviorSnapshot(val effectiveFromPatch: GamePatch?, val behavior: DropProbabilityBehavior)
