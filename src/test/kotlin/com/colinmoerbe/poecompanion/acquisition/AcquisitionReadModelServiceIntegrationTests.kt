package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.encounter.persistence.SpringDataBossEncounterKnowledgeRepository
import com.colinmoerbe.poecompanion.acquisition.persistence.AcquisitionSourcePersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.persistence.DropRelationshipPersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.persistence.SpringDataAvailableAttemptsRepository
import com.colinmoerbe.poecompanion.acquisition.persistence.SpringDataDropRelationshipKnowledgeRepository
import com.colinmoerbe.poecompanion.acquisition.persistence.SpringDataDropRelationshipRepository
import com.colinmoerbe.poecompanion.acquisition.persistence.SpringDataFixedDropEstimateRepository
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
import java.time.Instant

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
    @Autowired private val fixedEstimateService: FixedDropEstimateService,
    @Autowired private val fixedEstimateRepository: SpringDataFixedDropEstimateRepository,
    @Autowired private val attemptsService: AvailableAttemptsService,
    @Autowired private val attemptsRepository: SpringDataAvailableAttemptsRepository,
) {
    private val version = CompatibilityVersion(3, 30)
    private val broad = AcquisitionKnowledgeScope(version, Ruleset.NORMAL)

    @BeforeEach
    fun cleanBeforeTest() {
        selectionRepository.deleteAllInBatch()
        attemptsRepository.deleteAllInBatch()
        fixedEstimateRepository.deleteAllInBatch()
        encounterRepository.deleteAllInBatch()
        relationshipKnowledgeRepository.deleteAllInBatch()
        relationshipRepository.deleteAllInBatch()
    }

    @AfterEach
    fun cleanAfterTest() {
        selectionRepository.deleteAllInBatch()
        attemptsRepository.deleteAllInBatch()
        fixedEstimateRepository.deleteAllInBatch()
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
        relationshipKnowledgeService.recordSnapshot(
            relationship.id,
            specific,
            drop(DropRelationshipState.CANNOT_DROP),
            null,
        )

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

    @Test
    fun `known fixed probability and twelve recorded attempts should produce the canonical outlook`() {
        val accountId = selectAccount()
        val target = unique()
        val relationship = relationship(target)
        makeApplicable(relationship)
        val published = estimate()
        fixedEstimateService.publish(relationship.id, broad, published, null)
        attemptsService.setAvailableAttempts(accountId, relationship.acquisitionSourceId, 12)

        val assessed = evaluated(target).sources.single()
        val calculation = requireNotNull(assessed.calculation)

        assertThat(assessed.probabilityKnowledge).isEqualTo(
            ScopedAcquisitionKnowledge.Selected(FixedDropEstimateResolution.Resolved(published)),
        )
        assertThat(assessed.availableAttempts)
            .isEqualTo(AvailableAttempts(accountId, relationship.acquisitionSourceId, 12))
        assertThat(calculation.attemptDefinition)
            .isEqualTo(CompletedBossEncounterAttempt(relationship.acquisitionSourceId))
        assertThat(calculation.probabilityWithinAvailableAttempts).isCloseTo(
            0.717570463519,
            org.assertj.core.data.Offset.offset(1e-10),
        )
        assertThat(calculation.expectedAttemptsUntilSuccess).isEqualTo(10.0)
        assertThat(calculation.attemptsFor50Percent).isEqualTo(7)
        assertThat(calculation.attemptsFor75Percent).isEqualTo(14)
        assertThat(calculation.attemptsFor90Percent).isEqualTo(22)
        assertThat(calculation.attemptsFor95Percent).isEqualTo(29)
        assertThat(calculation.attemptsFor99Percent).isEqualTo(44)
    }

    @Test
    fun `no attempt entry must differ from an explicitly recorded zero`() {
        val accountId = selectAccount()
        val target = unique()
        val relationship = relationship(target)
        makeApplicable(relationship)
        fixedEstimateService.publish(relationship.id, broad, estimate(), null)

        val missingAttempts = evaluated(target).sources.single()
        assertThat(missingAttempts.availableAttempts).isNull()
        assertThat(missingAttempts.calculation?.probabilityWithinAvailableAttempts).isNull()
        assertThat(missingAttempts.calculation?.attemptsFor95Percent).isEqualTo(29)

        attemptsService.setAvailableAttempts(accountId, relationship.acquisitionSourceId, 0)
        val zeroAttempts = evaluated(target).sources.single()
        assertThat(zeroAttempts.availableAttempts?.count).isZero()
        assertThat(zeroAttempts.calculation?.probabilityWithinAvailableAttempts).isEqualTo(0.0)
        assertThat(zeroAttempts.calculation?.attemptsFor95Percent).isEqualTo(29)
    }

    @Test
    fun `unknown and negative prerequisite facts should retain knowledge without actionable calculations`() {
        selectAccount()
        val target = unique()
        val unknownSource = relationship(target)
        val unavailableSource = relationship(target)
        val cannotDrop = relationship(target)
        val missingProbability = relationship(target)

        relationshipKnowledgeService.recordSnapshot(unknownSource.id, broad, drop(), null)
        encounterService.recordSnapshot(
            unavailableSource.acquisitionSourceId,
            broad,
            encounter(AcquisitionSourceAvailability.UNAVAILABLE),
            null,
        )
        relationshipKnowledgeService.recordSnapshot(unavailableSource.id, broad, drop(), null)
        encounterService.recordSnapshot(cannotDrop.acquisitionSourceId, broad, encounter(), null)
        relationshipKnowledgeService.recordSnapshot(
            cannotDrop.id,
            broad,
            drop(DropRelationshipState.CANNOT_DROP),
            null,
        )
        makeApplicable(missingProbability)

        for (relationship in listOf(unknownSource, unavailableSource, cannotDrop)) {
            fixedEstimateService.publish(relationship.id, broad, estimate(), null)
        }

        val byId = evaluated(target).sources.associateBy { it.relationship.id }
        for (relationship in listOf(unknownSource, unavailableSource, cannotDrop)) {
            val assessment = byId.getValue(relationship.id)
            assertThat(assessment.calculation).isNull()
            assertThat(assessment.probabilityKnowledge).isInstanceOf(ScopedAcquisitionKnowledge.Selected::class.java)
        }
        assertThat(byId.getValue(missingProbability.id).probabilityKnowledge)
            .isEqualTo(ScopedAcquisitionKnowledge.Missing)
        assertThat(byId.getValue(missingProbability.id).calculation).isNull()
    }

    @Test
    fun `general and selected-league probability timelines should be ambiguous even when rates agree`() {
        val leagueId = selectLeague()
        val target = unique()
        val relationship = relationship(target)
        makeApplicable(relationship)
        fixedEstimateService.publish(relationship.id, broad, estimate(), null)
        fixedEstimateService.publish(relationship.id, broad.copy(leagueDefinitionId = leagueId), estimate(), null)

        val assessed = evaluated(target).sources.single()

        assertThat(assessed.sourceKnowledge).isInstanceOf(ScopedAcquisitionKnowledge.Selected::class.java)
        assertThat(assessed.relationshipKnowledge).isInstanceOf(ScopedAcquisitionKnowledge.Selected::class.java)
        assertThat(assessed.probabilityKnowledge).isEqualTo(ScopedAcquisitionKnowledge.Ambiguous)
        assertThat(assessed.calculation).isNull()
    }

    @Test
    fun `another league or compatibility version must not compete with accepted probability`() {
        selectLeague()
        val target = unique()
        val relationship = relationship(target)
        makeApplicable(relationship)
        fixedEstimateService.publish(relationship.id, broad, estimate(), null)
        fixedEstimateService.publish(
            relationship.id,
            broad.copy(leagueDefinitionId = league()),
            estimate(probability = 0.25),
            null,
        )
        fixedEstimateService.publish(
            relationship.id,
            AcquisitionKnowledgeScope(CompatibilityVersion(3, 31), Ruleset.NORMAL),
            estimate(probability = 0.30),
            null,
        )

        val assessed = evaluated(target).sources.single()
        assertThat(assessed.probabilityKnowledge)
            .isInstanceOf(ScopedAcquisitionKnowledge.Selected::class.java)
        assertThat(assessed.calculation?.expectedAttemptsUntilSuccess).isEqualTo(10.0)
    }

    @Test
    fun `withdrawal must not resurrect old probability after its effective patch`() {
        val accountId = selectAccount()
        val target = unique()
        val relationship = relationship(target)
        makeApplicable(relationship)
        fixedEstimateService.publish(relationship.id, broad, estimate(), null)
        fixedEstimateService.withdraw(
            relationship.id,
            broad,
            FixedProbabilityWithdrawal(
                id = FixedProbabilityWithdrawalId.generate(),
                effectiveFromPatch = GamePatch(version, 2),
                reason = "Evidence invalidated",
                evidenceReference = "test-withdrawal-evidence",
                reviewedAt = Instant.parse("2026-10-01T00:00:00Z"),
            ),
            null,
        )

        accountEvaluationService.select(accountId, version, GamePatch(version, 1))
        assertThat(evaluated(target).sources.single().calculation?.attemptsFor95Percent).isEqualTo(29)

        accountEvaluationService.select(accountId, version, GamePatch(version, 3))
        val later = evaluated(target).sources.single()
        assertThat(later.probabilityKnowledge)
            .isEqualTo(ScopedAcquisitionKnowledge.Selected(FixedDropEstimateResolution.Unknown))
        assertThat(later.calculation).isNull()

        accountEvaluationService.select(accountId, version)
        assertThat(evaluated(target).sources.single().calculation).isNull()
    }

    @Test
    fun `personalized attempt counts must be isolated by account and shared across relationships of a source`() {
        val firstAccount = selectAccount()
        val target = unique()
        val siblingUnique = unique()
        val primary = relationship(target)
        val sibling =
            DropRelationship(DropRelationshipId.generate(), primary.acquisitionSourceId, siblingUnique)
                .also(relationshipAdapter::save)
        makeApplicable(primary)
        relationshipKnowledgeService.recordSnapshot(sibling.id, broad, drop(), null)
        fixedEstimateService.publish(primary.id, broad, estimate(), null)
        fixedEstimateService.publish(sibling.id, broad, estimate(), null)
        attemptsService.setAvailableAttempts(firstAccount, primary.acquisitionSourceId, 12)

        val first = evaluated(target).sources.single()
        val siblingAssessment = evaluated(siblingUnique).sources.single()
        assertThat(first.calculation?.probabilityWithinAvailableAttempts)
            .isEqualTo(siblingAssessment.calculation?.probabilityWithinAvailableAttempts)
        assertThat(first.availableAttempts?.count).isEqualTo(12)

        val secondAccount = selectAccount()
        attemptsService.setAvailableAttempts(secondAccount, primary.acquisitionSourceId, 0)
        assertThat(evaluated(target).sources.single().calculation?.probabilityWithinAvailableAttempts).isEqualTo(0.0)
        accountEvaluationService.select(firstAccount, version)
        assertThat(evaluated(target).sources.single().availableAttempts?.count).isEqualTo(12)
    }

    private fun estimate(probability: Double = 0.10, patch: GamePatch? = null): FixedDropEstimateSnapshot =
        FixedDropEstimateSnapshot(
            id = DropEstimateId.generate(),
            effectiveFromPatch = patch,
            model = FixedBernoulliModel(probability),
            provenance = DropEstimateProvenance.MEASURED,
            evidenceReference = "test-evidence",
            reviewedAt = Instant.parse("2026-10-01T00:00:00Z"),
        )

    private fun makeApplicable(relationship: DropRelationship) {
        encounterService.recordSnapshot(relationship.acquisitionSourceId, broad, encounter(), null)
        relationshipKnowledgeService.recordSnapshot(relationship.id, broad, drop(), null)
    }

    private fun selectAccount(): AccountContextId {
        selectLeague()
        return requireNotNull(accountEvaluationService.getSelected()).accountContextId
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
