package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.account.persistence.SpringDataManualOwnershipRepository
import com.colinmoerbe.poecompanion.build.persistence.BuildArchetypePersistenceAdapter
import com.colinmoerbe.poecompanion.build.persistence.BuildVariantPersistenceAdapter
import com.colinmoerbe.poecompanion.build.persistence.BuildVariantRevisionPersistenceAdapter
import com.colinmoerbe.poecompanion.build.persistence.SpringDataBuildArchetypeRepository
import com.colinmoerbe.poecompanion.build.persistence.SpringDataBuildVariantRepository
import com.colinmoerbe.poecompanion.build.persistence.SpringDataBuildVariantRevisionRepository
import com.colinmoerbe.poecompanion.build.persistence.SpringDataRequirementGroupRepository
import com.colinmoerbe.poecompanion.build.persistence.SpringDataRequirementRepository
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.catalog.persistence.SpringDataUniqueDefinitionRepository
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.GameRealm
import com.colinmoerbe.poecompanion.league.LeagueDefinition
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.league.persistence.AccountContextPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.LeagueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountContextRepository
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountEvaluationSelectionRepository
import com.colinmoerbe.poecompanion.league.persistence.SpringDataLeagueDefinitionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * Verifies composition of selected account context, current ownership, active revisions, and pure readiness evaluation.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class BuildReadinessServiceIntegrationTests(
    @Autowired private val buildReadinessService: BuildReadinessService,
    @Autowired private val activationService: BuildVariantRevisionActivationService,
    @Autowired private val accountEvaluationService: AccountEvaluationService,
    @Autowired private val manualOwnershipService: ManualOwnershipService,
    @Autowired private val archetypePersistenceAdapter: BuildArchetypePersistenceAdapter,
    @Autowired private val variantPersistenceAdapter: BuildVariantPersistenceAdapter,
    @Autowired private val revisionPersistenceAdapter: BuildVariantRevisionPersistenceAdapter,
    @Autowired private val uniqueDefinitionPersistenceAdapter: UniqueDefinitionPersistenceAdapter,
    @Autowired private val leagueDefinitionPersistenceAdapter: LeagueDefinitionPersistenceAdapter,
    @Autowired private val accountContextPersistenceAdapter: AccountContextPersistenceAdapter,
    @Autowired private val requirementRepository: SpringDataRequirementRepository,
    @Autowired private val groupRepository: SpringDataRequirementGroupRepository,
    @Autowired private val revisionRepository: SpringDataBuildVariantRevisionRepository,
    @Autowired private val variantRepository: SpringDataBuildVariantRepository,
    @Autowired private val archetypeRepository: SpringDataBuildArchetypeRepository,
    @Autowired private val manualOwnershipRepository: SpringDataManualOwnershipRepository,
    @Autowired private val selectionRepository: SpringDataAccountEvaluationSelectionRepository,
    @Autowired private val accountContextRepository: SpringDataAccountContextRepository,
    @Autowired private val leagueDefinitionRepository: SpringDataLeagueDefinitionRepository,
    @Autowired private val uniqueDefinitionRepository: SpringDataUniqueDefinitionRepository,
) {

    @BeforeEach
    fun clearReadinessDataBeforeTest() {
        clearReadinessData()
    }

    @AfterEach
    fun clearReadinessDataAfterTest() {
        clearReadinessData()
    }

    @Test
    fun `selected account ownership and compatible active revision should compose into readiness`() {
        val accountContext = createPersistedAccountContext()
        val version = CompatibilityVersion(3, 30)
        accountEvaluationService.select(accountContext.id, version)
        val unique = createPersistedUniqueDefinition()
        manualOwnershipService.setOwnedQuantity(accountContext.id, unique.id, 1)
        val variant = createPersistedBuildVariant()
        val revision =
            createActiveRevision(
                variant.id,
                version,
                listOf(
                    RequirementGroup(
                        RequirementImportance.CORE,
                        RequirementLogic.ALL,
                        listOf(Requirement(unique.id, 2)),
                    ),
                ),
            )

        val result = buildReadinessService.evaluate(variant.id)

        val evaluated = result as BuildReadinessQueryResult.Evaluated
        assertThat(evaluated.accountEvaluation.accountContextId).isEqualTo(accountContext.id)
        assertThat(evaluated.revision.id).isEqualTo(revision.id)
        assertThat(evaluated.readiness.readiness).isEqualTo(UniqueReadiness.PARTIAL)
        assertThat(evaluated.readiness.groups.single().requirements.single().ownedQuantity).isEqualTo(1)
    }

    @Test
    fun `missing account selection should be explicit`() {
        val variant = createPersistedBuildVariant()

        val result = buildReadinessService.evaluate(variant.id)

        assertThat(result).isEqualTo(BuildReadinessQueryResult.NoAccountEvaluationSelected)
    }

    @Test
    fun `active revision from another compatibility version should not be evaluated`() {
        val accountContext = createPersistedAccountContext()
        accountEvaluationService.select(accountContext.id, CompatibilityVersion(3, 30))
        val variant = createPersistedBuildVariant()
        createActiveRevision(variant.id, CompatibilityVersion(3, 29))

        val result = buildReadinessService.evaluate(variant.id)

        val unavailable = result as BuildReadinessQueryResult.NoCompatibleActiveRevision
        assertThat(unavailable.accountEvaluation.accountContextId).isEqualTo(accountContext.id)
        assertThat(unavailable.accountEvaluation.evaluationContext.compatibilityVersion)
            .isEqualTo(CompatibilityVersion(3, 30))
    }

    @Test
    fun `ownership from another account context should not satisfy selected build requirements`() {
        val selectedContext = createPersistedAccountContext()
        val otherContext = createPersistedAccountContext()
        val version = CompatibilityVersion(3, 30)
        accountEvaluationService.select(selectedContext.id, version)
        val unique = createPersistedUniqueDefinition()
        manualOwnershipService.setOwnedQuantity(otherContext.id, unique.id, 1)
        val variant = createPersistedBuildVariant()
        createActiveRevision(
            variant.id,
            version,
            listOf(
                RequirementGroup(
                    RequirementImportance.CORE,
                    RequirementLogic.ALL,
                    listOf(Requirement(unique.id, 1)),
                ),
            ),
        )

        val result = buildReadinessService.evaluate(variant.id)

        val evaluated = result as BuildReadinessQueryResult.Evaluated
        assertThat(evaluated.readiness.readiness).isEqualTo(UniqueReadiness.PARTIAL)
        assertThat(evaluated.readiness.groups.single().requirements.single().ownedQuantity).isZero()
    }

    @Test
    fun `one owned unique should independently satisfy multiple build variants`() {
        val accountContext = createPersistedAccountContext()
        val version = CompatibilityVersion(3, 30)
        accountEvaluationService.select(accountContext.id, version)
        val unique = createPersistedUniqueDefinition()
        manualOwnershipService.setOwnedQuantity(accountContext.id, unique.id, 1)
        val firstVariant = createPersistedBuildVariant()
        val secondVariant = createPersistedBuildVariant()
        val requirementGroups =
            listOf(
                RequirementGroup(
                    RequirementImportance.CORE,
                    RequirementLogic.ALL,
                    listOf(Requirement(unique.id, 1)),
                ),
            )
        createActiveRevision(firstVariant.id, version, requirementGroups)
        createActiveRevision(secondVariant.id, version, requirementGroups)

        val first = buildReadinessService.evaluate(firstVariant.id) as BuildReadinessQueryResult.Evaluated
        val second = buildReadinessService.evaluate(secondVariant.id) as BuildReadinessQueryResult.Evaluated

        assertThat(first.readiness.readiness).isEqualTo(UniqueReadiness.UNIQUE_READY)
        assertThat(second.readiness.readiness).isEqualTo(UniqueReadiness.UNIQUE_READY)
        assertThat(first.readiness.groups.single().requirements.single().ownedQuantity).isEqualTo(1)
        assertThat(second.readiness.groups.single().requirements.single().ownedQuantity).isEqualTo(1)
    }

    private fun createPersistedBuildVariant(): BuildVariant {
        val archetype =
            BuildArchetype(
                id = BuildArchetypeId.generate(),
                name = "Test archetype",
            ).also(archetypePersistenceAdapter::save)
        return BuildVariant(
            id = BuildVariantId.generate(),
            buildArchetypeId = archetype.id,
            label = null,
        ).also(variantPersistenceAdapter::save)
    }

    private fun createActiveRevision(
        buildVariantId: BuildVariantId,
        version: CompatibilityVersion,
        requirementGroups: List<RequirementGroup> = emptyList(),
    ): BuildVariantRevision = BuildVariantRevision(
        id = BuildVariantRevisionId.generate(),
        buildVariantId = buildVariantId,
        compatibilityVersion = version,
    ).also { revision ->
        revision.updateRequirementGroups(requirementGroups)
        revisionPersistenceAdapter.saveDraftSnapshot(revision)
        activationService.activate(revision.id)
    }

    private fun createPersistedUniqueDefinition(): UniqueDefinition =
        UniqueDefinition(UniqueDefinitionId.generate()).also(uniqueDefinitionPersistenceAdapter::save)

    private fun createPersistedAccountContext(): AccountContext {
        val leagueDefinition =
            LeagueDefinition(
                id = LeagueDefinitionId.generate(),
                name = "Allflame",
                type = LeagueType.CHALLENGE,
                participation = LeagueParticipation.SSF,
                mortality = LeagueMortality.SOFTCORE,
                realm = GameRealm.PC,
            ).also(leagueDefinitionPersistenceAdapter::save)
        return AccountContext(
            id = AccountContextId.generate(),
            leagueDefinitionId = leagueDefinition.id,
            ruleset = Ruleset.NORMAL,
        ).also(accountContextPersistenceAdapter::save)
    }

    private fun clearReadinessData() {
        manualOwnershipRepository.deleteAllInBatch()
        requirementRepository.deleteAllInBatch()
        groupRepository.deleteAllInBatch()
        revisionRepository.deleteAllInBatch()
        variantRepository.deleteAllInBatch()
        archetypeRepository.deleteAllInBatch()
        selectionRepository.deleteAllInBatch()
        accountContextRepository.deleteAllInBatch()
        leagueDefinitionRepository.deleteAllInBatch()
        uniqueDefinitionRepository.deleteAllInBatch()
    }
}
