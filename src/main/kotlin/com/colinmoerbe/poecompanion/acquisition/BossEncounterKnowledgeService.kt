package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.acquisition.encounter.internal.BossEncounterKnowledgePersistencePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Curates versioned source availability, labels and boss participation without interpreting drop relationships.
 */
@Service
class BossEncounterKnowledgeService internal constructor(
    private val persistencePort: BossEncounterKnowledgePersistencePort,
) {
    @Transactional
    fun recordSnapshot(
        acquisitionSourceId: AcquisitionSourceId,
        scope: AcquisitionKnowledgeScope,
        snapshot: BossEncounterKnowledgeSnapshot,
        expectedRevision: Long?,
    ): Long {
        require(expectedRevision == null || expectedRevision >= 0) { "Expected revision must not be negative" }
        require(persistencePort.sourceExists(acquisitionSourceId)) {
            "Acquisition source must exist before encounter knowledge can be curated"
        }
        BossEncounterKnowledgeTimeline(acquisitionSourceId, scope, listOf(snapshot))
        if (snapshot.bossParticipation is BossParticipation.Fixed) {
            require(persistencePort.bossDefinitionsExist(snapshot.bossParticipation.bossDefinitionIds)) {
                "All fixed encounter participants must reference existing BossDefinitions"
            }
        }
        return persistencePort.recordSnapshot(acquisitionSourceId, scope, snapshot, expectedRevision)
    }

    @Transactional(readOnly = true)
    fun findAcceptedChangePoints(
        acquisitionSourceId: AcquisitionSourceId,
        scope: AcquisitionKnowledgeScope,
    ): List<AcceptedBossEncounterChangePoint> = persistencePort.findAcceptedChangePoints(acquisitionSourceId, scope)

    @Transactional(readOnly = true)
    fun findTimeline(
        acquisitionSourceId: AcquisitionSourceId,
        scope: AcquisitionKnowledgeScope,
    ): BossEncounterKnowledgeTimeline? {
        val snapshots = persistencePort.findAcceptedChangePoints(acquisitionSourceId, scope).map { it.snapshot }
        return if (snapshots.isEmpty()) null else BossEncounterKnowledgeTimeline(acquisitionSourceId, scope, snapshots)
    }
}
