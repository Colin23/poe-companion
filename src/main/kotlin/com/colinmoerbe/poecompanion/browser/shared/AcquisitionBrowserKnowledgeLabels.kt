package com.colinmoerbe.poecompanion.browser.shared

import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeResolution
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipResolution
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipState
import com.colinmoerbe.poecompanion.acquisition.FixedDropEstimateResolution
import com.colinmoerbe.poecompanion.acquisition.ScopedAcquisitionKnowledge
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
            null -> "Unknown — no account selected"

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
        null -> "Unknown — no account selected"

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
        null -> "Unknown — no account selected"

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
        return String.format(Locale.ROOT, "%.3f%%", estimate.model.probabilityPerAttempt * PERCENT_MULTIPLIER)
    }

    private const val PERCENT_MULTIPLIER = 100.0
}
