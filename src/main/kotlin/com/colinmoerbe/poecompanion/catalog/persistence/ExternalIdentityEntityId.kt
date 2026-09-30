package com.colinmoerbe.poecompanion.catalog.persistence

import com.colinmoerbe.poecompanion.catalog.ExternalProvider
import java.io.Serializable

/**
 * Composite JPA identity for an external provider mapping.
 */
internal data class ExternalIdentityEntityId(
    var provider: ExternalProvider = ExternalProvider.POE_WIKI,
    var providerKey: String = "",
) : Serializable
