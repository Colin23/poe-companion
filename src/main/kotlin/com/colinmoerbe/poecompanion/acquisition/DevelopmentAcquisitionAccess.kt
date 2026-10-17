package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.acquisition.persistence.AcquisitionSourcePersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.persistence.DropRelationshipPersistenceAdapter
import org.springframework.stereotype.Component

/** Narrow development-bootstrap entry point; stable acquisition identity persistence stays module-internal. */
@Component
internal class DevelopmentAcquisitionAccess(
    private val sources: AcquisitionSourcePersistenceAdapter,
    private val relationships: DropRelationshipPersistenceAdapter,
) {
    fun findSourceById(id: AcquisitionSourceId): AcquisitionSource? = sources.findById(id)

    fun findRelationshipById(id: DropRelationshipId): DropRelationship? = relationships.findById(id)

    fun saveSource(source: AcquisitionSource) = sources.save(source)

    fun saveRelationship(relationship: DropRelationship) = relationships.save(relationship)
}
