package com.colinmoerbe.poecompanion.acquisition.overrides.persistence

import com.colinmoerbe.poecompanion.acquisition.overrides.DropRateOverride
import com.colinmoerbe.poecompanion.acquisition.overrides.DropRateOverrideConflictException
import com.colinmoerbe.poecompanion.acquisition.overrides.DropRateOverrideKey
import com.colinmoerbe.poecompanion.acquisition.overrides.DropRateOverrideRevisionId
import com.colinmoerbe.poecompanion.acquisition.overrides.internal.DropRateOverridePersistencePort
import com.colinmoerbe.poecompanion.acquisition.probability.FixedBernoulliModel
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Stores immutable personal confirmation revisions with persistence-only ACTIVE/SUPERSEDED/REMOVED status.
 *
 * The AccountContext's immutable league and ruleset are authoritative, so they are not duplicated in the table.
 * All operations here are invoked through an owner-authorizing application service.
 */
@Repository
internal class DropRateOverridePersistenceAdapter(
    private val repository: SpringDataDropRateOverrideRevisionRepository,
) : DropRateOverridePersistencePort {
    @Transactional(readOnly = true)
    override fun findCurrent(key: DropRateOverrideKey): DropRateOverride? {
        val revision = repository.findActiveByExactKey(
            accountId = key.accountContextId.value,
            relationshipId = key.dropRelationshipId.value,
            major = key.evaluationContext.compatibilityVersion.major,
            minor = key.evaluationContext.compatibilityVersion.minor,
            patchNumber = key.evaluationContext.gamePatch?.patch,
            patchSuffix = key.evaluationContext.gamePatch?.suffix,
        ) ?: return null
        return DropRateOverride(
            revisionId = DropRateOverrideRevisionId(revision.id),
            key = key,
            model = FixedBernoulliModel(revision.probabilityPerAttempt),
        )
    }

    @Transactional
    override fun confirm(confirmation: DropRateOverride, expectedRevisionId: DropRateOverrideRevisionId?) {
        val key = confirmation.key
        if (expectedRevisionId != null) {
            endExpected(key, expectedRevisionId, DropRateOverrideRevisionStatus.SUPERSEDED)
        }

        try {
            repository.insertActive(
                revisionId = confirmation.revisionId.value,
                accountId = key.accountContextId.value,
                relationshipId = key.dropRelationshipId.value,
                major = key.evaluationContext.compatibilityVersion.major,
                minor = key.evaluationContext.compatibilityVersion.minor,
                patchNumber = key.evaluationContext.gamePatch?.patch,
                patchSuffix = key.evaluationContext.gamePatch?.suffix,
                probability = confirmation.model.probabilityPerAttempt,
            )
        } catch (exception: DataIntegrityViolationException) {
            if (exception.hasConstraintName(ACTIVE_KEY_UNIQUE_INDEX) ||
                exception.hasConstraintName(REVISION_PRIMARY_KEY)
            ) {
                throw DropRateOverrideConflictException(
                    "An override confirmation already occupies this exact context or revision identity",
                    exception,
                )
            }
            throw exception
        }
    }

    @Transactional
    override fun remove(key: DropRateOverrideKey, expectedRevisionId: DropRateOverrideRevisionId) {
        endExpected(key, expectedRevisionId, DropRateOverrideRevisionStatus.REMOVED)
    }

    private fun endExpected(
        key: DropRateOverrideKey,
        expectedRevisionId: DropRateOverrideRevisionId,
        nextStatus: DropRateOverrideRevisionStatus,
    ) {
        val changed = repository.endActiveIfExpected(
            expectedRevisionId = expectedRevisionId.value,
            nextStatus = nextStatus.name,
            accountId = key.accountContextId.value,
            relationshipId = key.dropRelationshipId.value,
            major = key.evaluationContext.compatibilityVersion.major,
            minor = key.evaluationContext.compatibilityVersion.minor,
            patchNumber = key.evaluationContext.gamePatch?.patch,
            patchSuffix = key.evaluationContext.gamePatch?.suffix,
        )
        if (changed != 1) {
            throw DropRateOverrideConflictException(
                "Expected current personal override revision was not found in this exact context",
            )
        }
    }

    private fun Throwable.hasConstraintName(constraintName: String): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is ConstraintViolationException && current.constraintName == constraintName) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private companion object {
        const val ACTIVE_KEY_UNIQUE_INDEX = "uq_drop_rate_override_active_key"
        const val REVISION_PRIMARY_KEY = "pk_drop_rate_override_revision"
    }
}
