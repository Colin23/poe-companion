package com.colinmoerbe.poecompanion.acquisition.encounter

/**
 * Canonical authored availability state for a known acquisition source.
 *
 * Unknown/not-verified availability is derived when applicable knowledge cannot be resolved and is not stored here.
 */
enum class AcquisitionSourceAvailability {
    AVAILABLE,
    UNAVAILABLE,
}
