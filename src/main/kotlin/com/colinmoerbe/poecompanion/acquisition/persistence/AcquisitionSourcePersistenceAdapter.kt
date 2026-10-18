package com.colinmoerbe.poecompanion.acquisition.persistence

import com.colinmoerbe.poecompanion.acquisition.AcquisitionSource
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceId
import org.springframework.stereotype.Repository

/**
 * Persists canonical acquisition source identities without exposing JPA representations to the domain.
 */
@Repository
internal class AcquisitionSourcePersistenceAdapter(
    private val springDataRepository: SpringDataAcquisitionSourceRepository,
) {
    fun save(acquisitionSource: AcquisitionSource) {
        springDataRepository.save(AcquisitionSourceEntity(acquisitionSource.id.value))
    }

    fun findAll(): List<AcquisitionSource> = springDataRepository.findAll().map { entity ->
        AcquisitionSource(AcquisitionSourceId(entity.id))
    }

    fun findById(id: AcquisitionSourceId): AcquisitionSource? = springDataRepository
        .findById(id.value)
        .map { entity -> AcquisitionSource(AcquisitionSourceId(entity.id)) }
        .orElse(null)
}
