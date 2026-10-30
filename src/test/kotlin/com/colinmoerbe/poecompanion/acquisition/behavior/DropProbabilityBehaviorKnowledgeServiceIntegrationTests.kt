package com.colinmoerbe.poecompanion.acquisition.behavior

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.behavior.persistence.SpringDataDropProbabilityBehaviorKnowledgeRepository
import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.relationship.persistence.DropRelationshipPersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.relationship.persistence.SpringDataDropRelationshipRepository
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSource
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.persistence.AcquisitionSourcePersistenceAdapter
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GamePatch
import com.colinmoerbe.poecompanion.league.GameRealm
import com.colinmoerbe.poecompanion.league.LeagueDefinition
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.league.persistence.LeagueDefinitionPersistenceAdapter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.test.context.support.WithMockUser
import java.util.UUID

/**
 * PostgreSQL integration contract for independently accepted probability behavior, including curator authorization.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.user.username=test-user",
        "poe-companion.security.user.password=test-password",
        "poe-companion.security.admin.username=test-admin",
        "poe-companion.security.admin.password=test-admin-password",
    ],
)
@WithMockUser(username = "test-admin", roles = ["ADMIN"])
internal class DropProbabilityBehaviorKnowledgeServiceIntegrationTests(
    @Autowired private val service: DropProbabilityBehaviorKnowledgeService,
    @Autowired private val repository: SpringDataDropProbabilityBehaviorKnowledgeRepository,
    @Autowired private val relationshipRepository: SpringDataDropRelationshipRepository,
    @Autowired private val relationships: DropRelationshipPersistenceAdapter,
    @Autowired private val sources: AcquisitionSourcePersistenceAdapter,
    @Autowired private val uniques: UniqueDefinitionPersistenceAdapter,
    @Autowired private val leagues: LeagueDefinitionPersistenceAdapter,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val version = CompatibilityVersion(3, 30)
    private val scope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL)

    @BeforeEach
    fun beforeTest() {
        repository.deleteAllInBatch()
        relationshipRepository.deleteAllInBatch()
    }

    @AfterEach
    fun afterTest() {
        repository.deleteAllInBatch()
        relationshipRepository.deleteAllInBatch()
    }

    @Test
    fun `missing behavior is unknown rather than an implicitly fixed probability`() {
        val id = relationship().id
        assertThat(service.findTimeline(id, scope)).isNull()
        assertThat(service.findAcceptedChangePoints(id, scope)).isEmpty()
    }

    @Test
    fun `fixed behavior is accepted without requiring a canonical numeric estimate`() {
        val id = relationship().id
        val fixed = snapshot()
        val revision = service.recordSnapshot(id, scope, fixed, null)

        assertThat(revision).isZero()
        assertThat(service.findAcceptedChangePoints(id, scope))
            .containsExactly(AcceptedDropProbabilityBehaviorChangePoint(fixed, 0L))
        assertThat(service.findTimeline(id, scope)?.resolve(context()))
            .isEqualTo(DropProbabilityBehaviorResolution.Resolved(fixed))
    }

    @Test
    fun `modifier dependent behavior is accepted without requiring a numeric estimate`() {
        val id = relationship().id
        val modified = snapshot(behavior = DropProbabilityBehavior.MODIFIER_DEPENDENT)
        service.recordSnapshot(id, scope, modified, null)

        assertThat(service.findTimeline(id, scope)?.resolve(context()))
            .isEqualTo(DropProbabilityBehaviorResolution.Resolved(modified))
    }

    @Test
    fun `different patches may change behavior without rewriting prior knowledge`() {
        val id = relationship().id
        val baseline = snapshot()
        val changed = snapshot(GamePatch(version, 2), DropProbabilityBehavior.MODIFIER_DEPENDENT)
        service.recordSnapshot(id, scope, changed, null)
        service.recordSnapshot(id, scope, baseline, null)

        val timeline = service.findTimeline(id, scope)
        assertThat(timeline?.snapshots).containsExactly(baseline, changed)
        assertThat(timeline?.resolve(context(GamePatch(version, 1))))
            .isEqualTo(DropProbabilityBehaviorResolution.Resolved(baseline))
        assertThat(timeline?.resolve(context(GamePatch(version, 3))))
            .isEqualTo(DropProbabilityBehaviorResolution.Resolved(changed))
        assertThat(timeline?.resolve(context()))
            .isEqualTo(DropProbabilityBehaviorResolution.Unknown)
    }

    @Test
    fun `correction advances its token and stale corrections cannot overwrite it`() {
        val id = relationship().id
        val original = snapshot()
        val changed = snapshot(behavior = DropProbabilityBehavior.MODIFIER_DEPENDENT)
        service.recordSnapshot(id, scope, original, null)

        assertThat(service.recordSnapshot(id, scope, changed, 0L)).isEqualTo(1L)
        assertThatThrownBy { service.recordSnapshot(id, scope, original, 0L) }
            .isInstanceOf(DropProbabilityBehaviorKnowledgeConflictException::class.java)
        assertThat(service.findAcceptedChangePoints(id, scope))
            .containsExactly(AcceptedDropProbabilityBehaviorChangePoint(changed, 1L))
        assertThat(repository.count()).isEqualTo(1L)
    }

    @Test
    fun `attempted correction of another patch must not create or alter a change point`() {
        val id = relationship().id
        service.recordSnapshot(id, scope, snapshot(), null)

        assertThatThrownBy {
            service.recordSnapshot(id, scope, snapshot(GamePatch(version, 3)), 0L)
        }.isInstanceOf(DropProbabilityBehaviorKnowledgeConflictException::class.java)
        assertThat(repository.count()).isEqualTo(1L)
    }

    @Test
    fun `newly authored duplicate baseline must be rejected instead of replacing a fact`() {
        val id = relationship().id
        service.recordSnapshot(id, scope, snapshot(), null)

        assertThatThrownBy {
            service.recordSnapshot(id, scope, snapshot(behavior = DropProbabilityBehavior.MODIFIER_DEPENDENT), null)
        }.isInstanceOf(DropProbabilityBehaviorKnowledgeConflictException::class.java)
        assertThat(repository.count()).isEqualTo(1L)
    }

    @Test
    fun `version ruleset and league identity have independent knowledge scopes`() {
        val id = relationship().id
        val leagueId = league().id
        val ruthless = AcquisitionKnowledgeScope(version, Ruleset.RUTHLESS)
        val nextVersion = AcquisitionKnowledgeScope(CompatibilityVersion(3, 31), Ruleset.NORMAL)
        val leagueSpecific = AcquisitionKnowledgeScope(version, Ruleset.NORMAL, leagueId)
        service.recordSnapshot(id, scope, snapshot(), null)
        service.recordSnapshot(id, ruthless, snapshot(behavior = DropProbabilityBehavior.MODIFIER_DEPENDENT), null)
        service.recordSnapshot(id, nextVersion, snapshot(), null)
        service.recordSnapshot(id, leagueSpecific, snapshot(behavior = DropProbabilityBehavior.MODIFIER_DEPENDENT), null)

        assertThat(service.findTimeline(id, scope)?.snapshots).containsExactly(snapshot())
        assertThat(service.findTimeline(id, ruthless)?.snapshots?.single()?.behavior)
            .isEqualTo(DropProbabilityBehavior.MODIFIER_DEPENDENT)
        assertThat(service.findTimeline(id, nextVersion)?.resolve(context()))
            .isEqualTo(DropProbabilityBehaviorResolution.DoesNotApply)
        assertThat(service.findTimeline(id, leagueSpecific)?.resolve(context()))
            .isEqualTo(DropProbabilityBehaviorResolution.Unknown)
        assertThat(
            service.findTimeline(id, leagueSpecific)?.resolve(context(leagueDefinitionId = leagueId)),
        ).isEqualTo(
            DropProbabilityBehaviorResolution.Resolved(
                snapshot(behavior = DropProbabilityBehavior.MODIFIER_DEPENDENT),
            ),
        )
    }

    @Test
    fun `invalid relationship patch version and negative revision must not persist`() {
        assertThatThrownBy {
            service.recordSnapshot(DropRelationshipId.generate(), scope, snapshot(), null)
        }.isInstanceOf(IllegalArgumentException::class.java)
        val id = relationship().id
        assertThatThrownBy {
            service.recordSnapshot(id, scope, snapshot(GamePatch(CompatibilityVersion(3, 31), 1)), null)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            service.recordSnapshot(id, scope, snapshot(), -1L)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repository.count()).isZero()
    }

    @Test
    @WithMockUser(username = "test-user")
    fun `ordinary player cannot author or correct canonical probability behavior`() {
        val id = relationship().id
        assertThatThrownBy { service.recordSnapshot(id, scope, snapshot(), null) }
            .isInstanceOf(AccessDeniedException::class.java)
        assertThat(repository.count()).isZero()
    }

    @Test
    fun `database rejects duplicate null-scope change points invalid behavior and malformed patch`() {
        val id = relationship().id
        service.recordSnapshot(id, scope, snapshot(), null)

        assertThatThrownBy { insertRaw(id) }.isInstanceOf(DuplicateKeyException::class.java)
        assertThatThrownBy { insertRaw(id, patchNumber = 5, behavior = "UNKNOWN") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRaw(id, patchSuffix = "a") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRaw(DropRelationshipId.generate()) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database enforces precise league and patch uniqueness`() {
        val id = relationship().id
        val leagueId = league().id
        service.recordSnapshot(
            id,
            AcquisitionKnowledgeScope(version, Ruleset.NORMAL, leagueId),
            snapshot(GamePatch(version, 2, "a")),
            null,
        )

        assertThatThrownBy { insertRaw(id, leagueId, 2, "a") }
            .isInstanceOf(DuplicateKeyException::class.java)
        assertThatThrownBy { insertRaw(id, LeagueDefinitionId.generate()) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun snapshot(
        patch: GamePatch? = null,
        behavior: DropProbabilityBehavior = DropProbabilityBehavior.FIXED_INDEPENDENT,
    ): DropProbabilityBehaviorSnapshot = DropProbabilityBehaviorSnapshot(patch, behavior)

    private fun context(
        patch: GamePatch? = null,
        leagueDefinitionId: LeagueDefinitionId? = null,
    ): EvaluationContext = EvaluationContext(version, Ruleset.NORMAL, patch, leagueDefinitionId)

    private fun relationship(): DropRelationship {
        val source = AcquisitionSource(AcquisitionSourceId.generate()).also(sources::save)
        val unique = UniqueDefinition(UniqueDefinitionId.generate()).also(uniques::save)
        return DropRelationship(DropRelationshipId.generate(), source.id, unique.id).also(relationships::save)
    }

    private fun league(): LeagueDefinition = LeagueDefinition(
        id = LeagueDefinitionId.generate(),
        name = "Behavior test ${UUID.randomUUID()}",
        type = LeagueType.CHALLENGE,
        participation = LeagueParticipation.SSF,
        mortality = LeagueMortality.SOFTCORE,
        realm = GameRealm.PC,
    ).also(leagues::save)

    private fun insertRaw(
        id: DropRelationshipId,
        leagueId: LeagueDefinitionId? = null,
        patchNumber: Int? = null,
        patchSuffix: String? = null,
        behavior: String = "FIXED_INDEPENDENT",
    ) {
        jdbc.update(
            """
            INSERT INTO poe_companion.drop_probability_behavior_knowledge (
                id, drop_relationship_id, compatibility_version_major, compatibility_version_minor,
                ruleset, league_definition_id, effective_patch_number, effective_patch_suffix,
                behavior, revision
            ) VALUES (?, ?, 3, 30, 'NORMAL', ?, ?, ?, ?, 0)
            """.trimIndent(),
            UUID.randomUUID(),
            id.value,
            leagueId?.value,
            patchNumber,
            patchSuffix,
            behavior,
        )
    }
}
