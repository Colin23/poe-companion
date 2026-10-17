package com.colinmoerbe.poecompanion.acquisition.persistence

/** Relational discriminator for the existing BossParticipation sealed variants. */
internal enum class BossEncounterParticipationKind {
    FIXED,
    CONFIGURATION_DEPENDENT,
}
