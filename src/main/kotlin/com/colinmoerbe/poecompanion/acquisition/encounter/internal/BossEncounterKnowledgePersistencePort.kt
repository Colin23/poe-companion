package com.colinmoerbe.poecompanion.acquisition.encounter.internal

import com.colinmoerbe.poecompanion.acquisition.encounter.AcceptedBossEncounterChangePoint
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeSnapshot
import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinitionId

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
