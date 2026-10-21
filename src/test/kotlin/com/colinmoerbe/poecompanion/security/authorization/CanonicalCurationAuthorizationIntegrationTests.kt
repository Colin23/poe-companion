package com.colinmoerbe.poecompanion.security.authorization

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.encounter.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeSnapshot
import com.colinmoerbe.poecompanion.acquisition.encounter.BossParticipation
import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.probability.DropEstimateId
import com.colinmoerbe.poecompanion.acquisition.probability.DropEstimateProvenance
import com.colinmoerbe.poecompanion.acquisition.probability.FixedBernoulliModel
import com.colinmoerbe.poecompanion.acquisition.probability.FixedDropEstimateService
import com.colinmoerbe.poecompanion.acquisition.probability.FixedDropEstimateSnapshot
import com.colinmoerbe.poecompanion.acquisition.probability.FixedProbabilityWithdrawal
import com.colinmoerbe.poecompanion.acquisition.probability.FixedProbabilityWithdrawalId
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipSnapshot
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipState
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinition
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinitionId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinitionService
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevisionActivationService
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevisionId
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.Ruleset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.test.context.support.WithMockUser
import java.time.Instant

/** Guards the application-command boundary, even without an HTTP curator controller. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.user.username=test-user",
        "poe-companion.security.user.password=test-password",
        "poe-companion.security.admin.username=test-admin",
        "poe-companion.security.admin.password=test-admin-password",
    ],
)
internal class CanonicalCurationAuthorizationIntegrationTests(
    @Autowired private val bosses: BossDefinitionService,
    @Autowired private val encounters: BossEncounterKnowledgeService,
    @Autowired private val relationships: DropRelationshipKnowledgeService,
    @Autowired private val estimates: FixedDropEstimateService,
    @Autowired private val activation: BuildVariantRevisionActivationService,
) {
    private val scope = AcquisitionKnowledgeScope(CompatibilityVersion(3, 30), Ruleset.NORMAL)
    private val reviewedAt = Instant.parse("2026-10-01T00:00:00Z")

    @Test
    @WithMockUser(username = "test-user", roles = ["USER"])
    fun `USER cannot invoke any canonical authoring command`() {
        assertThatThrownBy { bosses.create(BossDefinition(BossDefinitionId.generate(), "Forbidden Test Boss")) }
            .isInstanceOf(AccessDeniedException::class.java)

        assertThatThrownBy {
            encounters.recordSnapshot(
                AcquisitionSourceId.generate(),
                scope,
                BossEncounterKnowledgeSnapshot(
                    null,
                    "Forbidden source",
                    AcquisitionSourceAvailability.AVAILABLE,
                    BossParticipation.ConfigurationDependent,
                ),
                null,
            )
        }.isInstanceOf(AccessDeniedException::class.java)

        assertThatThrownBy {
            relationships.recordSnapshot(
                DropRelationshipId.generate(),
                scope,
                DropRelationshipSnapshot(null, DropRelationshipState.CAN_DROP),
                null,
            )
        }.isInstanceOf(AccessDeniedException::class.java)

        val relationship = DropRelationshipId.generate()
        assertThatThrownBy {
            estimates.publish(
                relationship,
                scope,
                FixedDropEstimateSnapshot(
                    DropEstimateId.generate(),
                    null,
                    FixedBernoulliModel(0.1),
                    DropEstimateProvenance.MEASURED,
                    "synthetic-test",
                    reviewedAt,
                ),
                null,
            )
        }.isInstanceOf(AccessDeniedException::class.java)

        assertThatThrownBy {
            estimates.withdraw(
                relationship,
                scope,
                FixedProbabilityWithdrawal(
                    FixedProbabilityWithdrawalId.generate(),
                    null,
                    "Evidence invalidated",
                    "synthetic-test",
                    reviewedAt,
                ),
                null,
            )
        }.isInstanceOf(AccessDeniedException::class.java)

        assertThatThrownBy { activation.activate(BuildVariantRevisionId.generate()) }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    @WithMockUser(username = "test-admin", roles = ["ADMIN"])
    fun `ADMIN can create shared canonical boss knowledge`() {
        val id = BossDefinitionId.generate()
        val definition = BossDefinition(id, "Admin test boss ${id.value}")

        bosses.create(definition)

        assertThat(bosses.findById(definition.id)?.displayName).isEqualTo(definition.displayName)
    }

    @Test
    fun `anonymous caller cannot invoke canonical authoring commands`() {
        assertThatThrownBy { bosses.create(BossDefinition(BossDefinitionId.generate(), "Anonymous test boss")) }
            .isInstanceOf(AccessDeniedException::class.java)
    }
}
