package com.colinmoerbe.poecompanion.acquisition.encounter

import com.colinmoerbe.poecompanion.acquisition.AcceptedBossEncounterChangePoint
import com.colinmoerbe.poecompanion.acquisition.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.BossDefinitionId
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeSnapshot

/**
 * Persists full accepted snapshots at exact source, scope and effective-patch change points.
 */
internal interface BossEncounterKnowledgePersistencePort {
    fun sourceExists(id: AcquisitionSourceId): Boolean

    fun bossDefinitionsExist(ids: Set<BossDefinitionId>): Boolean

    /**
     * A null expected revision publishes into an empty change point; otherwise replace that exact accepted fact.
     * The returned revision is zero for a new fact or expectedRevision + 1 for a correction.
     */
    fun recordSnapshot(
        acquisitionSourceId: AcquisitionSourceId,
        scope: AcquisitionKnowledgeScope,
        snapshot: BossEncounterKnowledgeSnapshot,
        expectedRevision: Long?,
    ): Long

    fun findAcceptedChangePoints(
        acquisitionSourceId: AcquisitionSourceId,
        scope: AcquisitionKnowledgeScope,
    ): List<AcceptedBossEncounterChangePoint>
}
