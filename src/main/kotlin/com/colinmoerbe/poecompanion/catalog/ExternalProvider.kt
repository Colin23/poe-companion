package com.colinmoerbe.poecompanion.catalog

/**
 * External identity namespaces recognized by the Companion.
 *
 * This set is code-owned rather than runtime configuration. Recognizing an identity namespace does not imply that an
 * ingestion adapter for that provider has already been implemented.
 */
enum class ExternalProvider {
    POE_WIKI,
    REPOE,
}
