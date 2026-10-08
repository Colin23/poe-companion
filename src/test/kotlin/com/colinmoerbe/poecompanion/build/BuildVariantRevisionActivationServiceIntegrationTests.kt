package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
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
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class BuildVariantRevisionActivationServiceIntegrationTests(
    @Autowired private val activationService: BuildVariantRevisionActivationService,
    @Autowired private val revisionPersistenceAdapter: BuildVariantRevisionPersistenceAdapter,
    @Autowired private val archetypePersistenceAdapter: BuildArchetypePersistenceAdapter,
    @Autowired private val variantPersistenceAdapter: BuildVariantPersistenceAdapter,
    @Autowired private val uniqueDefinitionPersistenceAdapter: UniqueDefinitionPersistenceAdapter,
    @Autowired private val archetypeRepository: SpringDataBuildArchetypeRepository,
    @Autowired private val variantRepository: SpringDataBuildVariantRepository,
    @Autowired private val revisionRepository: SpringDataBuildVariantRevisionRepository,
    @Autowired private val groupRepository: SpringDataRequirementGroupRepository,
    @Autowired private val requirementRepository: SpringDataRequirementRepository,
    @Autowired transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    @BeforeEach
    fun clearBuildPersistenceBeforeTest() {
        clearBuildPersistence()
    }

    @AfterEach
    fun clearBuildPersistenceAfterTest() {
        clearBuildPersistence()
    }

    @Test
    fun `activating first draft should preserve its semantic snapshot`() {
        val variant = createPersistedVariant()
        val unique = createPersistedUniqueDefinition()
        val revision =
            createDraft(
                variantId = variant.id,
                version = CompatibilityVersion(3, 30),
                groups =
                listOf(
                    RequirementGroup(
                        RequirementImportance.CORE,
                        RequirementLogic.ALL,
                        listOf(Requirement(unique.id, 2)),
                    ),
                ),
            )

        activationService.activate(revision.id)

        val restored = requireNotNull(revisionPersistenceAdapter.findById(revision.id))
        assertThat(restored.status).isEqualTo(BuildVariantRevisionStatus.ACTIVE)
        assertThat(restored.requirementGroups).hasSize(1)
        assertThat(restored.requirementGroups.single().requirements)
            .containsExactly(Requirement(unique.id, 2))
    }

    @Test
    @Transactional
    fun `activated revision should reread as active inside the same outer transaction`() {
        val variant = createPersistedVariant()
        val revision = createDraft(variant.id, CompatibilityVersion(3, 30))

        activationService.activate(revision.id)

        assertThat(revisionPersistenceAdapter.findById(revision.id)?.status)
            .isEqualTo(BuildVariantRevisionStatus.ACTIVE)
    }

    @Test
    fun `activating replacement should supersede only active revision in the same scope`() {
        val firstVariant = createPersistedVariant()
        val secondVariant = createPersistedVariant()
        val version = CompatibilityVersion(3, 30)
        val previous = createDraft(firstVariant.id, version)
        val otherVersion = createDraft(firstVariant.id, CompatibilityVersion(3, 31))
        val otherVariant = createDraft(secondVariant.id, version)
        val replacement = createDraft(firstVariant.id, version)

        activationService.activate(previous.id)
        activationService.activate(otherVersion.id)
        activationService.activate(otherVariant.id)

        activationService.activate(replacement.id)

        assertThat(revisionPersistenceAdapter.findById(previous.id)?.status)
            .isEqualTo(BuildVariantRevisionStatus.SUPERSEDED)
        assertThat(revisionPersistenceAdapter.findById(replacement.id)?.status)
            .isEqualTo(BuildVariantRevisionStatus.ACTIVE)
        assertThat(revisionPersistenceAdapter.findById(otherVersion.id)?.status)
            .isEqualTo(BuildVariantRevisionStatus.ACTIVE)
        assertThat(revisionPersistenceAdapter.findById(otherVariant.id)?.status)
            .isEqualTo(BuildVariantRevisionStatus.ACTIVE)
    }

    @Test
    fun `activation should reject unknown and non-draft targets`() {
        assertThatThrownBy { activationService.activate(BuildVariantRevisionId.generate()) }
            .isInstanceOf(IllegalArgumentException::class.java)

        val variant = createPersistedVariant()
        val previous = createDraft(variant.id, CompatibilityVersion(3, 30))
        val replacement = createDraft(variant.id, CompatibilityVersion(3, 30))
        activationService.activate(previous.id)

        assertThatThrownBy { activationService.activate(previous.id) }
            .isInstanceOf(IllegalStateException::class.java)

        activationService.activate(replacement.id)

        assertThatThrownBy { activationService.activate(previous.id) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `stale draft snapshot should not rewrite activated durable state`() {
        val variant = createPersistedVariant()
        val originalUnique = createPersistedUniqueDefinition()
        val replacementUnique = createPersistedUniqueDefinition()
        val revision =
            createDraft(
                variant.id,
                CompatibilityVersion(3, 30),
                listOf(
                    RequirementGroup(
                        RequirementImportance.CORE,
                        RequirementLogic.ALL,
                        listOf(Requirement(originalUnique.id, 1)),
                    ),
                ),
            )
        val staleDraft = requireNotNull(revisionPersistenceAdapter.findById(revision.id))
        staleDraft.updateRequirementGroups(
            listOf(
                RequirementGroup(
                    RequirementImportance.CORE,
                    RequirementLogic.ALL,
                    listOf(Requirement(replacementUnique.id, 1)),
                ),
            ),
        )

        activationService.activate(revision.id)

        assertThatThrownBy { revisionPersistenceAdapter.saveDraftSnapshot(staleDraft) }
            .isInstanceOf(BuildRevisionActivationConflictException::class.java)

        val restored = requireNotNull(revisionPersistenceAdapter.findById(revision.id))
        assertThat(restored.status).isEqualTo(BuildVariantRevisionStatus.ACTIVE)
        assertThat(restored.requirementGroups.single().requirements)
            .containsExactly(Requirement(originalUnique.id, 1))
    }

    @Test
    fun `competing activations without current active revision should yield one winner and one conflict`() {
        val variant = createPersistedVariant()
        val first = createDraft(variant.id, CompatibilityVersion(3, 30))
        val second = createDraft(variant.id, CompatibilityVersion(3, 30))
        val firstUpdated = CountDownLatch(1)
        val secondAttempting = CountDownLatch(1)
        val allowFirstCommit = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val firstResult =
                executor.submit<Throwable?> {
                    runCatching {
                        transactionTemplate.executeWithoutResult {
                            revisionPersistenceAdapter.transitionStatus(
                                first.id,
                                BuildVariantRevisionStatus.DRAFT,
                                BuildVariantRevisionStatus.ACTIVE,
                            )
                            firstUpdated.countDown()
                            check(allowFirstCommit.await(10, TimeUnit.SECONDS))
                        }
                    }.exceptionOrNull()
                }

            check(firstUpdated.await(10, TimeUnit.SECONDS))

            val secondResult =
                executor.submit<Throwable?> {
                    runCatching {
                        transactionTemplate.executeWithoutResult {
                            secondAttempting.countDown()
                            revisionPersistenceAdapter.transitionStatus(
                                second.id,
                                BuildVariantRevisionStatus.DRAFT,
                                BuildVariantRevisionStatus.ACTIVE,
                            )
                        }
                    }.exceptionOrNull()
                }

            check(secondAttempting.await(10, TimeUnit.SECONDS))
            allowFirstCommit.countDown()

            assertThat(firstResult.get(10, TimeUnit.SECONDS)).isNull()
            assertThat(secondResult.get(10, TimeUnit.SECONDS))
                .isInstanceOf(BuildRevisionActivationConflictException::class.java)

            val active =
                revisionPersistenceAdapter.findActiveBy(
                    variant.id,
                    CompatibilityVersion(3, 30),
                )
            assertThat(active?.id).isEqualTo(first.id)
        } finally {
            allowFirstCommit.countDown()
            executor.shutdownNow()
        }
    }

    private fun createPersistedVariant(): BuildVariant {
        val archetype = BuildArchetype(BuildArchetypeId.generate(), "Test archetype")
        val variant = BuildVariant(BuildVariantId.generate(), archetype.id, null)
        archetypePersistenceAdapter.save(archetype)
        variantPersistenceAdapter.save(variant)
        return variant
    }

    private fun createPersistedUniqueDefinition(): UniqueDefinition =
        UniqueDefinition(UniqueDefinitionId.generate()).also(uniqueDefinitionPersistenceAdapter::save)

    private fun createDraft(
        variantId: BuildVariantId,
        version: CompatibilityVersion,
        groups: List<RequirementGroup> = emptyList(),
    ): BuildVariantRevision = BuildVariantRevision(
        id = BuildVariantRevisionId.generate(),
        buildVariantId = variantId,
        compatibilityVersion = version,
    ).also { revision ->
        revision.updateRequirementGroups(groups)
        revisionPersistenceAdapter.saveDraftSnapshot(revision)
    }

    private fun clearBuildPersistence() {
        requirementRepository.deleteAllInBatch()
        groupRepository.deleteAllInBatch()
        revisionRepository.deleteAllInBatch()
        variantRepository.deleteAllInBatch()
        archetypeRepository.deleteAllInBatch()
    }
}
