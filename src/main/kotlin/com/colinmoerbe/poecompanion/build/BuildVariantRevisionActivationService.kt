package com.colinmoerbe.poecompanion.build

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Atomically activates one draft revision and supersedes the previous active revision for the same scope.
 */
@Service
class BuildVariantRevisionActivationService internal constructor(
    private val persistencePort: BuildVariantRevisionPersistencePort,
) {
    @Transactional
    fun activate(id: BuildVariantRevisionId) {
        val target =
            requireNotNull(persistencePort.findById(id)) {
                "Build variant revision does not exist: $id"
            }
        check(target.status == BuildVariantRevisionStatus.DRAFT) {
            "Only draft build revisions can be activated"
        }

        persistencePort
            .findActiveBy(target.buildVariantId, target.compatibilityVersion)
            ?.let { previousActive ->
                previousActive.supersede()
                persistencePort.transitionStatus(
                    id = previousActive.id,
                    expectedStatus = BuildVariantRevisionStatus.ACTIVE,
                    newStatus = BuildVariantRevisionStatus.SUPERSEDED,
                )
            }

        target.activate()
        persistencePort.transitionStatus(
            id = target.id,
            expectedStatus = BuildVariantRevisionStatus.DRAFT,
            newStatus = BuildVariantRevisionStatus.ACTIVE,
        )
    }
}
