package com.colinmoerbe.poecompanion.acquisition.persistence

import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipId
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Persists stable canonical source-to-Unique associations without exposing JPA representations to the domain.
 */
@Repository
internal class DropRelationshipPersistenceAdapter(
    private val springDataRepository: SpringDataDropRelationshipRepository,
) {
    @Transactional
    fun save(relationship: DropRelationship) {
        val insertedRows =
            springDataRepository.insertIfAbsent(
                id = relationship.id.value,
                acquisitionSourceId = relationship.acquisitionSourceId.value,
                uniqueDefinitionId = relationship.uniqueDefinitionId.value,
            )

        if (insertedRows == 1) {
            return
        }

        val existing =
            springDataRepository.findById(relationship.id.value).orElseThrow {
                IllegalStateException("Drop relationship disappeared after an identity conflict")
            }

        require(
            existing.acquisitionSourceId == relationship.acquisitionSourceId.value &&
                existing.uniqueDefinitionId == relationship.uniqueDefinitionId.value,
        ) {
            "Existing drop relationship identity cannot be rebound to another source or Unique"
        }
    }

    fun findById(id: DropRelationshipId): DropRelationship? {
        val entity = springDataRepository.findById(id.value).orElse(null) ?: return null
        return DropRelationship(
            id = DropRelationshipId(entity.id),
            acquisitionSourceId = AcquisitionSourceId(entity.acquisitionSourceId),
            uniqueDefinitionId = UniqueDefinitionId(entity.uniqueDefinitionId),
        )
    }
}
