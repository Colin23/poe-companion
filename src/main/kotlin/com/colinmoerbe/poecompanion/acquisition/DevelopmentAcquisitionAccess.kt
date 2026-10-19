package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.relationship.persistence.DropRelationshipPersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSource
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.persistence.AcquisitionSourcePersistenceAdapter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Narrow development-bootstrap entry point; stable acquisition identity persistence stays module-internal.
 *
 * Property-gated without @Profile("dev") so integration tests can opt in and initialize explicitly.
 */
@Component
@ConditionalOnProperty(
    prefix = "poe-companion.dev-fixture",
    name = ["enabled"],
    havingValue = "true",
)
internal class DevelopmentAcquisitionAccess(
    private val sources: AcquisitionSourcePersistenceAdapter,
    private val relationships: DropRelationshipPersistenceAdapter,
) {
    fun findSourceById(id: AcquisitionSourceId): AcquisitionSource? = sources.findById(id)

    fun findRelationshipById(id: DropRelationshipId): DropRelationship? = relationships.findById(id)

    fun saveSource(source: AcquisitionSource) = sources.save(source)

    fun saveRelationship(relationship: DropRelationship) = relationships.save(relationship)
}
