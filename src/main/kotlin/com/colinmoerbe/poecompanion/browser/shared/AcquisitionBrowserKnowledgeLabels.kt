package com.colinmoerbe.poecompanion.browser.shared

import com.colinmoerbe.poecompanion.acquisition.encounter.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeResolution
import com.colinmoerbe.poecompanion.acquisition.knowledge.ScopedAcquisitionKnowledge
import com.colinmoerbe.poecompanion.acquisition.probability.FixedDropEstimateResolution
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipResolution
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipState
import java.util.Locale

/** Explicit browser labels for missing, ambiguous, unknown and verified canonical facts. */
internal object AcquisitionBrowserKnowledgeLabels {
    fun sourceLabel(knowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>?): String {
        val resolution = (knowledge as? ScopedAcquisitionKnowledge.Selected<*>)?.resolution
        return if (resolution is BossEncounterKnowledgeResolution.Resolved) {
            resolution.snapshot.displayLabel
        } else {
            "Acquisition source (name unavailable)"
        }
    }

    fun sourceAvailability(knowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>?): String =
        when (knowledge) {
            null -> "Unknown — no game environment selected"

            ScopedAcquisitionKnowledge.Missing -> "Unknown — no accepted encounter knowledge"

            ScopedAcquisitionKnowledge.Ambiguous -> "Ambiguous encounter scope"

            is ScopedAcquisitionKnowledge.Selected -> when (val resolution = knowledge.resolution) {
                is BossEncounterKnowledgeResolution.Resolved -> when (resolution.snapshot.availability) {
                    AcquisitionSourceAvailability.AVAILABLE -> "AVAILABLE"
                    AcquisitionSourceAvailability.UNAVAILABLE -> "UNAVAILABLE"
                }

                BossEncounterKnowledgeResolution.Unknown -> "Unknown for selected patch"

                BossEncounterKnowledgeResolution.DoesNotApply -> "Not applicable to selected context"
            }
        }

    fun relationship(knowledge: ScopedAcquisitionKnowledge<DropRelationshipResolution>?): String = when (knowledge) {
        null -> "Unknown — no game environment selected"

        ScopedAcquisitionKnowledge.Missing -> "Unknown — no accepted relationship knowledge"

        ScopedAcquisitionKnowledge.Ambiguous -> "Ambiguous relationship scope"

        is ScopedAcquisitionKnowledge.Selected -> when (val resolution = knowledge.resolution) {
            is DropRelationshipResolution.Resolved -> when (resolution.snapshot.state) {
                DropRelationshipState.CAN_DROP -> "CAN_DROP"
                DropRelationshipState.CANNOT_DROP -> "CANNOT_DROP"
            }

            DropRelationshipResolution.Unknown -> "Unknown for selected patch"

            DropRelationshipResolution.DoesNotApply -> "Not applicable to selected context"
        }
    }

    fun estimate(knowledge: ScopedAcquisitionKnowledge<FixedDropEstimateResolution>?): String = when (knowledge) {
        null -> "Unknown — no game environment selected"

        ScopedAcquisitionKnowledge.Missing -> "No accepted fixed estimate"

        ScopedAcquisitionKnowledge.Ambiguous -> "Ambiguous estimate scope"

        is ScopedAcquisitionKnowledge.Selected -> when (knowledge.resolution) {
            is FixedDropEstimateResolution.Resolved -> "Accepted fixed estimate"
            FixedDropEstimateResolution.Unknown -> "Unknown or withdrawn for selected patch"
            FixedDropEstimateResolution.DoesNotApply -> "Not applicable to selected context"
        }
    }

    /** A recorded fixed estimate is independently meaningful, even if the source isn't actionable. */
    fun acceptedPerAttemptChance(knowledge: ScopedAcquisitionKnowledge<FixedDropEstimateResolution>?): String? {
        val resolution = (knowledge as? ScopedAcquisitionKnowledge.Selected<*>)?.resolution
        val estimate = (resolution as? FixedDropEstimateResolution.Resolved)?.snapshot ?: return null
        val percent = estimate.model.probabilityPerAttempt * PERCENT_MULTIPLIER
        return if (percent > 0.0 && percent < MIN_VISIBLE_PERCENT) {
            "<0.001%"
        } else {
            String.format(Locale.ROOT, "%.3f", percent).trimEnd('0').trimEnd('.') + "%"
        }
    }

    /** Browser-only interpretation of an encounter label; null knowledge means no environment. */
    fun sourceNameResolved(knowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>?): Boolean =
        (knowledge as? ScopedAcquisitionKnowledge.Selected<*>)?.resolution is BossEncounterKnowledgeResolution.Resolved

    fun sourceNameExplanation(knowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>?): String? =
        when (knowledge) {
            null -> "Name not evaluated"
            ScopedAcquisitionKnowledge.Missing -> "No accepted encounter name is recorded for this environment"
            ScopedAcquisitionKnowledge.Ambiguous -> "Accepted encounter knowledge is ambiguous for this environment"
            is ScopedAcquisitionKnowledge.Selected -> when (knowledge.resolution) {
                is BossEncounterKnowledgeResolution.Resolved -> null
                BossEncounterKnowledgeResolution.Unknown -> "Encounter name unknown for the selected patch"
                BossEncounterKnowledgeResolution.DoesNotApply -> "No applicable encounter name for this environment"
            }
        }

    fun readableSourceAvailability(knowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>?): String {
        val resolution = (knowledge as? ScopedAcquisitionKnowledge.Selected<*>)?.resolution
        return when (resolution) {
            is BossEncounterKnowledgeResolution.Resolved -> when (resolution.snapshot.availability) {
                AcquisitionSourceAvailability.AVAILABLE -> "Available"
                AcquisitionSourceAvailability.UNAVAILABLE -> "Unavailable"
            }
            BossEncounterKnowledgeResolution.DoesNotApply -> "Not applicable"
            else -> when (knowledge) {
                null -> "Not evaluated"
                ScopedAcquisitionKnowledge.Ambiguous -> "Ambiguous"
                else -> "Unknown"
            }
        }
    }

    fun sourceAvailabilityCssClass(knowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>?): String =
        when (readableSourceAvailability(knowledge)) {
            "Available" -> "available"
            "Unavailable" -> "unavailable"
            "Ambiguous" -> "ambiguous"
            "Not evaluated" -> "not-evaluated"
            "Not applicable" -> "not-applicable"
            else -> "unknown"
        }

    fun readableRelationship(knowledge: ScopedAcquisitionKnowledge<DropRelationshipResolution>?): String {
        val resolution = (knowledge as? ScopedAcquisitionKnowledge.Selected<*>)?.resolution
        return when (resolution) {
            is DropRelationshipResolution.Resolved -> when (resolution.snapshot.state) {
                DropRelationshipState.CAN_DROP -> "Can drop"
                DropRelationshipState.CANNOT_DROP -> "Cannot drop"
            }
            else -> relationship(knowledge)
        }
    }

    fun relationshipCssClass(knowledge: ScopedAcquisitionKnowledge<DropRelationshipResolution>?): String =
        when (readableRelationship(knowledge)) {
            "Can drop" -> "can-drop"
            "Cannot drop" -> "cannot-drop"
            else -> "unknown"
        }

    private const val MIN_VISIBLE_PERCENT = 0.001
    private const val PERCENT_MULTIPLIER = 100.0
}
