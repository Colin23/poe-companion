package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.persistence.AcquisitionSourcePersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.persistence.DropRelationshipPersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.persistence.SpringDataBossEncounterKnowledgeRepository
import com.colinmoerbe.poecompanion.acquisition.persistence.SpringDataDropRelationshipKnowledgeRepository
import com.colinmoerbe.poecompanion.acquisition.persistence.SpringDataDropRelationshipRepository
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.GamePatch
import com.colinmoerbe.poecompanion.league.GameRealm
import com.colinmoerbe.poecompanion.league.LeagueDefinition
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.league.persistence.AccountContextPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.LeagueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountEvaluationSelectionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * Proves source discovery and independent accepted knowledge resolution through PostgreSQL.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class AcquisitionReadModelServiceIntegrationTests(
    @Autowired private val readModel: AcquisitionReadModelService,
    @Autowired private val accountEvaluationService: AccountEvaluationService,
    @Autowired private val selectionRepository: SpringDataAccountEvaluationSelectionRepository,
    @Autowired private val accountContextAdapter: AccountContextPersistenceAdapter,
    @Autowired private val leagueAdapter: LeagueDefinitionPersistenceAdapter,
    @Autowired private val uniqueAdapter: UniqueDefinitionPersistenceAdapter,
    @Autowired private val sourceAdapter: AcquisitionSourcePersistenceAdapter,
    @Autowired private val relationshipAdapter: DropRelationshipPersistenceAdapter,
    @Autowired private val relationshipRepository: SpringDataDropRelationshipRepository,
    @Autowired private val encounterService: BossEncounterKnowledgeService,
    @Autowired private val encounterRepository: SpringDataBossEncounterKnowledgeRepository,
    @Autowired private val relationshipKnowledgeService: DropRelationshipKnowledgeService,
    @Autowired private val relationshipKnowledgeRepository: SpringDataDropRelationshipKnowledgeRepository,
) {
    private val version = CompatibilityVersion(3, 30)
    private val broad = AcquisitionKnowledgeScope(version, Ruleset.NORMAL)

    @BeforeEach
    fun cleanBeforeTest() {
        selectionRepository.deleteAllInBatch()
        encounterRepository.deleteAllInBatch()
        relationshipKnowledgeRepository.deleteAllInBatch()
        relationshipRepository.deleteAllInBatch()
    }

    @AfterEach
    fun cleanAfterTest() {
        selectionRepository.deleteAllInBatch()
        encounterRepository.deleteAllInBatch()
        relationshipKnowledgeRepository.deleteAllInBatch()
        relationshipRepository.deleteAllInBatch()
    }

    @Test
    fun `missing account selection should be an explicit query outcome`() {
        assertThat(readModel.findForUnique(UniqueDefinitionId.generate()))
            .isEqualTo(AcquisitionQueryResult.NoAccountEvaluationSelected)
    }

    @Test
    fun `missing relationship should not imply that no sources can produce the unique`() {
        selectLeague()

        val result = evaluated(UniqueDefinitionId.generate())

        assertThat(result.sources).isEmpty()
    }

    @Test
    fun `multiple sources should be discovered and their availability and drops resolved independently`() {
        selectLeague()
        val targetUnique = unique()
        val first = relationship(targetUnique)
        val second = relationship(targetUnique)
        val otherUnique = unique()
        relationship(otherUnique)
        encounterService.recordSnapshot(first.acquisitionSourceId, broad, encounter(), null)
        relationshipKnowledgeService.recordSnapshot(first.id, broad, drop(), null)
        relationshipKnowledgeService.recordSnapshot(second.id, broad, drop(DropRelationshipState.CANNOT_DROP), null)

        val result = evaluated(targetUnique)
        assertThat(result.sources).hasSize(2)
        val byId = result.sources.associateBy { it.relationship.id }
        assertThat(byId.keys).containsExactlyInAnyOrder(first.id, second.id)
        assertThat(byId.getValue(first.id).sourceKnowledge).isEqualTo(
            ScopedAcquisitionKnowledge.Selected(BossEncounterKnowledgeResolution.Resolved(encounter())),
        )
        assertThat(byId.getValue(first.id).relationshipKnowledge).isEqualTo(
            ScopedAcquisitionKnowledge.Selected(DropRelationshipResolution.Resolved(drop())),
        )
        assertThat(byId.getValue(second.id).sourceKnowledge).isEqualTo(ScopedAcquisitionKnowledge.Missing)
        assertThat(byId.getValue(second.id).relationshipKnowledge).isEqualTo(
            ScopedAcquisitionKnowledge.Selected(
                DropRelationshipResolution.Resolved(drop(DropRelationshipState.CANNOT_DROP)),
            ),
        )
    }

    @Test
    fun `two authored scopes should remain ambiguous independently for each kind of knowledge`() {
        val league = selectLeague()
        val unique = unique()
        val relationship = relationship(unique)
        val specific = broad.copy(leagueDefinitionId = league)
        encounterService.recordSnapshot(relationship.acquisitionSourceId, broad, encounter(), null)
        encounterService.recordSnapshot(relationship.acquisitionSourceId, specific, encounter(), null)
        relationshipKnowledgeService.recordSnapshot(relationship.id, broad, drop(), null)
        relationshipKnowledgeService.recordSnapshot(relationship.id, specific, drop(DropRelationshipState.CANNOT_DROP), null)

        val assessed = evaluated(unique).sources.single()

        assertThat(assessed.sourceKnowledge).isEqualTo(ScopedAcquisitionKnowledge.Ambiguous)
        assertThat(assessed.relationshipKnowledge).isEqualTo(ScopedAcquisitionKnowledge.Ambiguous)
    }

    @Test
    fun `league-specific knowledge for another league must not override broad knowledge`() {
        selectLeague()
        val anotherLeague = league()
        val unique = unique()
        val relationship = relationship(unique)
        encounterService.recordSnapshot(relationship.acquisitionSourceId, broad, encounter(), null)
        encounterService.recordSnapshot(
            relationship.acquisitionSourceId,
            broad.copy(leagueDefinitionId = anotherLeague),
            encounter(AcquisitionSourceAvailability.UNAVAILABLE),
            null,
        )

        assertThat(evaluated(unique).sources.single().sourceKnowledge).isEqualTo(
            ScopedAcquisitionKnowledge.Selected(BossEncounterKnowledgeResolution.Resolved(encounter())),
        )
    }

    @Test
    fun `patchless evaluation should not select a baseline when the known timeline changes at a later patch`() {
        selectLeague()
        val unique = unique()
        val relationship = relationship(unique)
        encounterService.recordSnapshot(relationship.acquisitionSourceId, broad, encounter(), null)
        encounterService.recordSnapshot(
            relationship.acquisitionSourceId,
            broad,
            encounter(AcquisitionSourceAvailability.UNAVAILABLE, GamePatch(version, 2)),
            null,
        )

        assertThat(evaluated(unique).sources.single().sourceKnowledge).isEqualTo(
            ScopedAcquisitionKnowledge.Selected(BossEncounterKnowledgeResolution.Unknown),
        )
    }

    @Test
    fun `selected account league should determine which scoped knowledge applies`() {
        val firstLeague = selectLeague()
        val secondLeague = league()
        val unique = unique()
        val relationship = relationship(unique)
        relationshipKnowledgeService.recordSnapshot(
            relationship.id,
            broad.copy(leagueDefinitionId = firstLeague),
            drop(),
            null,
        )
        relationshipKnowledgeService.recordSnapshot(
            relationship.id,
            broad.copy(leagueDefinitionId = secondLeague),
            drop(DropRelationshipState.CANNOT_DROP),
            null,
        )

        assertThat(evaluated(unique).sources.single().relationshipKnowledge).isEqualTo(
            ScopedAcquisitionKnowledge.Selected(DropRelationshipResolution.Resolved(drop())),
        )

        selectLeague(secondLeague)
        assertThat(evaluated(unique).sources.single().relationshipKnowledge).isEqualTo(
            ScopedAcquisitionKnowledge.Selected(
                DropRelationshipResolution.Resolved(drop(DropRelationshipState.CANNOT_DROP)),
            ),
        )
    }

    private fun evaluated(unique: UniqueDefinitionId): AcquisitionQueryResult.Evaluated =
        readModel.findForUnique(unique) as AcquisitionQueryResult.Evaluated

    private fun unique(): UniqueDefinitionId = UniqueDefinitionId.generate().also {
        uniqueAdapter.save(UniqueDefinition(it))
    }

    private fun relationship(unique: UniqueDefinitionId): DropRelationship {
        val sourceId = AcquisitionSourceId.generate()
        sourceAdapter.save(AcquisitionSource(sourceId))
        return DropRelationship(DropRelationshipId.generate(), sourceId, unique).also(relationshipAdapter::save)
    }

    private fun league(): LeagueDefinitionId = LeagueDefinitionId.generate().also {
        leagueAdapter.save(
            LeagueDefinition(
                id = it,
                name = "Acquisition integration league",
                type = LeagueType.CHALLENGE,
                participation = LeagueParticipation.SSF,
                mortality = LeagueMortality.SOFTCORE,
                realm = GameRealm.PC,
            ),
        )
    }

    private fun selectLeague(league: LeagueDefinitionId = league()): LeagueDefinitionId {
        val account = AccountContext(AccountContextId.generate(), league, Ruleset.NORMAL)
        accountContextAdapter.save(account)
        accountEvaluationService.select(account.id, version)
        return league
    }

    private fun encounter(
        availability: AcquisitionSourceAvailability = AcquisitionSourceAvailability.AVAILABLE,
        patch: GamePatch? = null,
    ) = BossEncounterKnowledgeSnapshot(patch, "Boss encounter", availability, BossParticipation.ConfigurationDependent)

    private fun drop(state: DropRelationshipState = DropRelationshipState.CAN_DROP) =
        DropRelationshipSnapshot(null, state)
}
