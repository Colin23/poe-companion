package com.colinmoerbe.poecompanion.acquisition.relationship

import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId

/**
 * Stable canonical association between one acquisition source and one Unique definition.
 *
 * Whether the source can currently produce the Unique is versioned knowledge and is not encoded in this identity
 * object.
 */
class DropRelationship(
    val id: DropRelationshipId,
    val acquisitionSourceId: AcquisitionSourceId,
    val uniqueDefinitionId: UniqueDefinitionId,
)
