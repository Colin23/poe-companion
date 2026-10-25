package com.colinmoerbe.poecompanion.browser.shared

import com.colinmoerbe.poecompanion.acquisition.encounter.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeResolution
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeSnapshot
import com.colinmoerbe.poecompanion.acquisition.encounter.BossParticipation
import com.colinmoerbe.poecompanion.acquisition.knowledge.ScopedAcquisitionKnowledge
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipResolution
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipSnapshot
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AcquisitionBrowserKnowledgeLabelsTests {
    @Test
    fun `cumulative chance formatting never disguises a small positive or sub-certain result`() {
        assertThat(AcquisitionBrowserProbabilityLabels.cumulativePercent(0.0)).isEqualTo("0.0%")
        assertThat(AcquisitionBrowserProbabilityLabels.cumulativePercent(0.000001)).isEqualTo("<0.1%")
        assertThat(AcquisitionBrowserProbabilityLabels.cumulativePercent(0.718)).isEqualTo("71.8%")
        assertThat(AcquisitionBrowserProbabilityLabels.cumulativePercent(0.999999)).isEqualTo(">99.9%")
        assertThat(AcquisitionBrowserProbabilityLabels.cumulativePercent(1.0)).isEqualTo("100.0%")
    }

    @Test
    fun `source availability CSS follows knowledge rather than translated labels`() {
        val available = ScopedAcquisitionKnowledge.Selected(
            BossEncounterKnowledgeResolution.Resolved(
                BossEncounterKnowledgeSnapshot(
                    null,
                    "Boss Y",
                    AcquisitionSourceAvailability.AVAILABLE,
                    BossParticipation.ConfigurationDependent,
                ),
            ),
        )
        assertThat(AcquisitionBrowserKnowledgeLabels.sourceAvailabilityCssClass(available)).isEqualTo("available")
        assertThat(
            AcquisitionBrowserKnowledgeLabels.sourceAvailabilityCssClass(
                ScopedAcquisitionKnowledge.Selected(BossEncounterKnowledgeResolution.DoesNotApply),
            ),
        ).isEqualTo("not-applicable")
        assertThat(AcquisitionBrowserKnowledgeLabels.sourceAvailabilityCssClass(ScopedAcquisitionKnowledge.Ambiguous))
            .isEqualTo("ambiguous")
        assertThat(AcquisitionBrowserKnowledgeLabels.sourceAvailabilityCssClass(null)).isEqualTo("not-evaluated")
    }

    @Test
    fun `relationship CSS follows verified drop state independently`() {
        val cannotDrop = ScopedAcquisitionKnowledge.Selected(
            DropRelationshipResolution.Resolved(DropRelationshipSnapshot(null, DropRelationshipState.CANNOT_DROP)),
        )
        assertThat(AcquisitionBrowserKnowledgeLabels.relationshipCssClass(cannotDrop)).isEqualTo("cannot-drop")
        assertThat(AcquisitionBrowserKnowledgeLabels.relationshipCssClass(ScopedAcquisitionKnowledge.Missing))
            .isEqualTo("unknown")
        assertThat(
            AcquisitionBrowserKnowledgeLabels.relationshipCssClass(
                ScopedAcquisitionKnowledge.Selected(DropRelationshipResolution.DoesNotApply),
            ),
        ).isEqualTo("unknown")
    }

    @Test
    fun `no game environment leaves a source identity known but its name unevaluated`() {
        assertThat(AcquisitionBrowserKnowledgeLabels.sourceNameResolved(null)).isFalse()
        assertThat(AcquisitionBrowserKnowledgeLabels.sourceNameExplanation(null)).isEqualTo("Name not evaluated")
        assertThat(AcquisitionBrowserKnowledgeLabels.readableSourceAvailability(null)).isEqualTo("Not evaluated")
    }
}
