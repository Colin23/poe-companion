package com.colinmoerbe.poecompanion.acquisition.relationship

/**
 * Explicit canonical knowledge about whether one established source-to-Unique relationship currently applies.
 *
 * Unknown relationship knowledge is derived during resolution and is not stored as a state.
 */
enum class DropRelationshipState {
    CAN_DROP,
    CANNOT_DROP,
}
