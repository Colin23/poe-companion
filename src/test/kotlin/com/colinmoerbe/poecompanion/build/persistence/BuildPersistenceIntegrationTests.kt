package com.colinmoerbe.poecompanion.build.persistence

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.build.BuildArchetype
import com.colinmoerbe.poecompanion.build.BuildArchetypeId
import com.colinmoerbe.poecompanion.build.BuildVariant
import com.colinmoerbe.poecompanion.build.BuildVariantId
import com.colinmoerbe.poecompanion.build.BuildVariantRevision
import com.colinmoerbe.poecompanion.build.BuildVariantRevisionId
import com.colinmoerbe.poecompanion.build.BuildVariantRevisionStatus
import com.colinmoerbe.poecompanion.build.Requirement
import com.colinmoerbe.poecompanion.build.RequirementGroup
import com.colinmoerbe.poecompanion.build.RequirementImportance
import com.colinmoerbe.poecompanion.build.RequirementLogic
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
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Verifies Build domain ↔ JPA ↔ PostgreSQL round-tripping and Build-specific schema invariants.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class BuildPersistenceIntegrationTests(
    @Autowired private val archetypePersistenceAdapter: BuildArchetypePersistenceAdapter,
    @Autowired private val variantPersistenceAdapter: BuildVariantPersistenceAdapter,
    @Autowired private val revisionPersistenceAdapter: BuildVariantRevisionPersistenceAdapter,
    @Autowired private val archetypeRepository: SpringDataBuildArchetypeRepository,
    @Autowired private val variantRepository: SpringDataBuildVariantRepository,
    @Autowired private val revisionRepository: SpringDataBuildVariantRevisionRepository,
    @Autowired private val groupRepository: SpringDataRequirementGroupRepository,
    @Autowired private val requirementRepository: SpringDataRequirementRepository,
    @Autowired private val uniqueDefinitionPersistenceAdapter: UniqueDefinitionPersistenceAdapter,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearBuildPersistenceBeforeTest() {
        clearBuildPersistence()
    }

    @AfterEach
    fun clearBuildPersistenceAfterTest() {
        clearBuildPersistence()
    }

    @Test
    fun `archetype and variant should retain application-owned identity and metadata`() {
        val archetype = BuildArchetype(BuildArchetypeId.generate(), "Lightning Arrow")
        val variant = BuildVariant(BuildVariantId.generate(), archetype.id, "Ballista")

        archetypePersistenceAdapter.save(archetype)
        variantPersistenceAdapter.save(variant)

        assertThat(archetypePersistenceAdapter.findById(archetype.id)?.id).isEqualTo(archetype.id)
        assertThat(archetypePersistenceAdapter.findById(archetype.id)?.name).isEqualTo("Lightning Arrow")
        assertThat(variantPersistenceAdapter.findById(variant.id)?.id).isEqualTo(variant.id)
        assertThat(variantPersistenceAdapter.findById(variant.id)?.buildArchetypeId).isEqualTo(archetype.id)
        assertThat(variantPersistenceAdapter.findById(variant.id)?.label).isEqualTo("Ballista")
    }

    @Test
    fun `revision snapshot should round trip with ordered groups and requirements`() {
        val persisted = createPersistedVariant()
        val firstUnique = createPersistedUniqueDefinition()
        val secondUnique = createPersistedUniqueDefinition()
        val thirdUnique = createPersistedUniqueDefinition()
        val revision =
            BuildVariantRevision(
                id = BuildVariantRevisionId.generate(),
                buildVariantId = persisted.variant.id,
                compatibilityVersion = CompatibilityVersion(3, 30),
            )
        revision.updateRequirementGroups(
            listOf(
                RequirementGroup(
                    importance = RequirementImportance.ENABLING,
                    logic = RequirementLogic.ALL,
                    requirements =
                    listOf(
                        Requirement(firstUnique.id, 1),
                        Requirement(secondUnique.id, 2),
                    ),
                ),
                RequirementGroup(
                    importance = RequirementImportance.CORE,
                    logic = RequirementLogic.ANY,
                    requirements = listOf(Requirement(thirdUnique.id, 1)),
                ),
            ),
        )

        revisionPersistenceAdapter.save(revision)
        val restored = requireNotNull(revisionPersistenceAdapter.findById(revision.id))

        assertThat(restored.id).isEqualTo(revision.id)
        assertThat(restored.buildVariantId).isEqualTo(revision.buildVariantId)
        assertThat(restored.compatibilityVersion).isEqualTo(CompatibilityVersion(3, 30))
        assertThat(restored.status).isEqualTo(BuildVariantRevisionStatus.DRAFT)
        assertThat(restored.requirementGroups.map { it.importance })
            .containsExactly(RequirementImportance.ENABLING, RequirementImportance.CORE)
        assertThat(restored.requirementGroups.map { it.logic })
            .containsExactly(RequirementLogic.ALL, RequirementLogic.ANY)
        assertThat(restored.requirementGroups[0].requirements)
            .containsExactly(
                Requirement(firstUnique.id, 1),
                Requirement(secondUnique.id, 2),
            )
        assertThat(restored.requirementGroups[1].requirements)
            .containsExactly(Requirement(thirdUnique.id, 1))
    }

    @Test
    fun `revision with zero requirement groups should round trip without synthetic children`() {
        val persisted = createPersistedVariant()
        val revision =
            BuildVariantRevision(
                id = BuildVariantRevisionId.generate(),
                buildVariantId = persisted.variant.id,
                compatibilityVersion = CompatibilityVersion(3, 30),
            )

        revisionPersistenceAdapter.save(revision)
        val restored = requireNotNull(revisionPersistenceAdapter.findById(revision.id))

        assertThat(restored.requirementGroups).isEmpty()
        assertThat(groupRepository.findAllByRevisionId(revision.id.value)).isEmpty()
        assertThat(requirementRepository.findAllByRevisionId(revision.id.value)).isEmpty()
    }

    @Test
    fun `saving edited draft should replace previous requirement snapshot and order`() {
        val persisted = createPersistedVariant()
        val firstUnique = createPersistedUniqueDefinition()
        val secondUnique = createPersistedUniqueDefinition()
        val removedUnique = createPersistedUniqueDefinition()
        val revision =
            BuildVariantRevision(
                id = BuildVariantRevisionId.generate(),
                buildVariantId = persisted.variant.id,
                compatibilityVersion = CompatibilityVersion(3, 30),
            )
        revision.updateRequirementGroups(
            listOf(
                RequirementGroup(
                    RequirementImportance.CORE,
                    RequirementLogic.ALL,
                    listOf(Requirement(removedUnique.id, 1)),
                ),
                RequirementGroup(
                    RequirementImportance.UPGRADE,
                    RequirementLogic.ANY,
                    listOf(Requirement(firstUnique.id, 1), Requirement(secondUnique.id, 1)),
                ),
            ),
        )
        revisionPersistenceAdapter.save(revision)

        revision.updateRequirementGroups(
            listOf(
                RequirementGroup(
                    RequirementImportance.UPGRADE,
                    RequirementLogic.ANY,
                    listOf(Requirement(secondUnique.id, 2), Requirement(firstUnique.id, 1)),
                ),
            ),
        )
        revisionPersistenceAdapter.save(revision)

        val restored = requireNotNull(revisionPersistenceAdapter.findById(revision.id))
        assertThat(restored.requirementGroups).hasSize(1)
        assertThat(restored.requirementGroups.single().importance).isEqualTo(RequirementImportance.UPGRADE)
        assertThat(restored.requirementGroups.single().requirements)
            .containsExactly(
                Requirement(secondUnique.id, 2),
                Requirement(firstUnique.id, 1),
            )
        assertThat(requirementRepository.findAllByRevisionId(revision.id.value).map { it.uniqueDefinitionId })
            .doesNotContain(removedUnique.id.value)
    }

    @Test
    fun `active revision should reconstruct through lifecycle and retain immutability`() {
        val persisted = createPersistedVariant()
        val unique = createPersistedUniqueDefinition()
        val revision =
            BuildVariantRevision(
                id = BuildVariantRevisionId.generate(),
                buildVariantId = persisted.variant.id,
                compatibilityVersion = CompatibilityVersion(3, 30),
            )
        revision.updateRequirementGroups(
            listOf(
                RequirementGroup(
                    RequirementImportance.CORE,
                    RequirementLogic.ALL,
                    listOf(Requirement(unique.id, 1)),
                ),
            ),
        )
        revision.activate()
        revisionPersistenceAdapter.save(revision)

        val restored = requireNotNull(revisionPersistenceAdapter.findById(revision.id))

        assertThat(restored.status).isEqualTo(BuildVariantRevisionStatus.ACTIVE)
        assertThatThrownBy { restored.updateRequirementGroups(emptyList()) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `superseded revision should reconstruct with superseded status`() {
        val persisted = createPersistedVariant()
        val revision =
            BuildVariantRevision(
                id = BuildVariantRevisionId.generate(),
                buildVariantId = persisted.variant.id,
                compatibilityVersion = CompatibilityVersion(3, 30),
            )
        revision.activate()
        revision.supersede()
        revisionPersistenceAdapter.save(revision)

        assertThat(revisionPersistenceAdapter.findById(revision.id)?.status)
            .isEqualTo(BuildVariantRevisionStatus.SUPERSEDED)
    }

    @Test
    fun `database should reject two active revisions for the same variant and compatibility version`() {
        val persisted = createPersistedVariant()

        insertRevision(persisted.variant.id, CompatibilityVersion(3, 30), BuildVariantRevisionStatus.ACTIVE)

        assertThatThrownBy {
            insertRevision(persisted.variant.id, CompatibilityVersion(3, 30), BuildVariantRevisionStatus.ACTIVE)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should allow historical and differently scoped revisions alongside an active revision`() {
        val first = createPersistedVariant()
        val second = createPersistedVariant()

        insertRevision(first.variant.id, CompatibilityVersion(3, 30), BuildVariantRevisionStatus.ACTIVE)
        insertRevision(first.variant.id, CompatibilityVersion(3, 30), BuildVariantRevisionStatus.DRAFT)
        insertRevision(first.variant.id, CompatibilityVersion(3, 30), BuildVariantRevisionStatus.SUPERSEDED)
        insertRevision(first.variant.id, CompatibilityVersion(3, 31), BuildVariantRevisionStatus.ACTIVE)
        insertRevision(second.variant.id, CompatibilityVersion(3, 30), BuildVariantRevisionStatus.ACTIVE)

        assertThat(revisionRepository.count()).isEqualTo(5L)
    }

    @Test
    fun `database should reject duplicate unique definitions inside one persisted group`() {
        val persisted = createPersistedVariant()
        val unique = createPersistedUniqueDefinition()
        val revisionId = insertRevision(
            persisted.variant.id,
            CompatibilityVersion(3, 30),
            BuildVariantRevisionStatus.DRAFT,
        )
        insertGroup(revisionId, 0)

        insertRequirement(revisionId, 0, 0, unique.id, 1)

        assertThatThrownBy {
            insertRequirement(revisionId, 0, 1, unique.id, 2)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should reject non-positive persisted requirement quantities`() {
        val persisted = createPersistedVariant()
        val unique = createPersistedUniqueDefinition()
        val revisionId = insertRevision(
            persisted.variant.id,
            CompatibilityVersion(3, 30),
            BuildVariantRevisionStatus.DRAFT,
        )
        insertGroup(revisionId, 0)

        assertThatThrownBy {
            insertRequirement(revisionId, 0, 0, unique.id, 0)
        }.isInstanceOf(DataIntegrityViolationException::class.java)

        assertThatThrownBy {
            insertRequirement(revisionId, 0, 0, unique.id, -1)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun createPersistedVariant(): PersistedVariant {
        val archetype = BuildArchetype(BuildArchetypeId.generate(), "Test archetype")
        val variant = BuildVariant(BuildVariantId.generate(), archetype.id, null)
        archetypePersistenceAdapter.save(archetype)
        variantPersistenceAdapter.save(variant)
        return PersistedVariant(archetype, variant)
    }

    private fun createPersistedUniqueDefinition(): UniqueDefinition =
        UniqueDefinition(UniqueDefinitionId.generate()).also(uniqueDefinitionPersistenceAdapter::save)

    private fun insertRevision(
        variantId: BuildVariantId,
        version: CompatibilityVersion,
        status: BuildVariantRevisionStatus,
    ): BuildVariantRevisionId {
        val revisionId = BuildVariantRevisionId.generate()
        jdbcTemplate.update(
            """
            INSERT INTO poe_companion.build_variant_revision (
                id,
                build_variant_id,
                compatibility_version_major,
                compatibility_version_minor,
                status
            )
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            revisionId.value,
            variantId.value,
            version.major,
            version.minor,
            status.name,
        )
        return revisionId
    }

    private fun insertGroup(revisionId: BuildVariantRevisionId, position: Int) {
        jdbcTemplate.update(
            """
            INSERT INTO poe_companion.build_requirement_group (
                build_variant_revision_id,
                position,
                importance,
                logic
            )
            VALUES (?, ?, 'CORE', 'ALL')
            """.trimIndent(),
            revisionId.value,
            position,
        )
    }

    private fun insertRequirement(
        revisionId: BuildVariantRevisionId,
        groupPosition: Int,
        position: Int,
        uniqueDefinitionId: UniqueDefinitionId,
        quantity: Int,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO poe_companion.build_requirement (
                build_variant_revision_id,
                group_position,
                position,
                unique_definition_id,
                required_quantity
            )
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            revisionId.value,
            groupPosition,
            position,
            uniqueDefinitionId.value,
            quantity,
        )
    }

    private fun clearBuildPersistence() {
        requirementRepository.deleteAllInBatch()
        groupRepository.deleteAllInBatch()
        revisionRepository.deleteAllInBatch()
        variantRepository.deleteAllInBatch()
        archetypeRepository.deleteAllInBatch()
    }

    private data class PersistedVariant(
        val archetype: BuildArchetype,
        val variant: BuildVariant,
    )
}
