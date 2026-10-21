package com.colinmoerbe.poecompanion.acquisition.relationship.persistence

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSource
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.persistence.AcquisitionSourcePersistenceAdapter
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
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
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Verifies persistence identity, pair uniqueness, and referential integrity for canonical drop relationships.
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
internal class DropRelationshipPersistenceIntegrationTests(
    @Autowired private val persistenceAdapter: DropRelationshipPersistenceAdapter,
    @Autowired private val springDataRepository: SpringDataDropRelationshipRepository,
    @Autowired private val sourcePersistenceAdapter: AcquisitionSourcePersistenceAdapter,
    @Autowired private val uniqueDefinitionPersistenceAdapter: UniqueDefinitionPersistenceAdapter,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {
    @BeforeEach
    fun clearDropRelationshipsBeforeTest() {
        springDataRepository.deleteAllInBatch()
    }

    @AfterEach
    fun clearDropRelationshipsAfterTest() {
        springDataRepository.deleteAllInBatch()
    }

    @Test
    fun `persisted relationship should retain stable source and Unique identities`() {
        val relationship = relationship()

        persistenceAdapter.save(relationship)

        val persisted = persistenceAdapter.findById(relationship.id)
        assertThat(persisted?.id).isEqualTo(relationship.id)
        assertThat(persisted?.acquisitionSourceId).isEqualTo(relationship.acquisitionSourceId)
        assertThat(persisted?.uniqueDefinitionId).isEqualTo(relationship.uniqueDefinitionId)
    }

    @Test
    fun `unknown relationship identity should not resolve`() {
        assertThat(persistenceAdapter.findById(DropRelationshipId.generate())).isNull()
    }

    @Test
    fun `saving the same ID and pair twice should be idempotent`() {
        val relationship = relationship()

        persistenceAdapter.save(relationship)
        persistenceAdapter.save(relationship)

        assertThat(springDataRepository.count()).isEqualTo(1L)
    }

    @Test
    fun `different source and Unique pairs should remain independent`() {
        val sourceA = createPersistedSource().id
        val sourceB = createPersistedSource().id
        val uniqueA = createPersistedUnique().id
        val uniqueB = createPersistedUnique().id

        val relationships =
            listOf(
                DropRelationship(DropRelationshipId.generate(), sourceA, uniqueA),
                DropRelationship(DropRelationshipId.generate(), sourceA, uniqueB),
                DropRelationship(DropRelationshipId.generate(), sourceB, uniqueA),
            )
        relationships.forEach(persistenceAdapter::save)

        assertThat(springDataRepository.count()).isEqualTo(3L)
        relationships.forEach { relationship ->
            assertThat(persistenceAdapter.findById(relationship.id)?.acquisitionSourceId)
                .isEqualTo(relationship.acquisitionSourceId)
        }
    }

    @Test
    fun `existing relationship ID must not be reassigned to a different source`() {
        val original = relationship()
        val differentSourceId = createPersistedSource().id
        persistenceAdapter.save(original)

        assertThatThrownBy {
            persistenceAdapter.save(
                DropRelationship(original.id, differentSourceId, original.uniqueDefinitionId),
            )
        }.isInstanceOf(InvalidDataAccessApiUsageException::class.java)
            .hasCauseInstanceOf(IllegalArgumentException::class.java)

        assertThat(persistenceAdapter.findById(original.id)?.acquisitionSourceId)
            .isEqualTo(original.acquisitionSourceId)
    }

    @Test
    fun `existing relationship ID must not be reassigned to a different Unique`() {
        val original = relationship()
        val differentUniqueId = createPersistedUnique().id
        persistenceAdapter.save(original)

        assertThatThrownBy {
            persistenceAdapter.save(
                DropRelationship(original.id, original.acquisitionSourceId, differentUniqueId),
            )
        }.isInstanceOf(InvalidDataAccessApiUsageException::class.java)
            .hasCauseInstanceOf(IllegalArgumentException::class.java)

        assertThat(persistenceAdapter.findById(original.id)?.uniqueDefinitionId)
            .isEqualTo(original.uniqueDefinitionId)
    }

    @Test
    fun `database should reject another ID for the same source and Unique pair`() {
        val original = relationship()
        persistenceAdapter.save(original)

        assertThatThrownBy {
            insertRelationship(
                DropRelationshipId.generate(),
                original.acquisitionSourceId,
                original.uniqueDefinitionId,
            )
        }.isInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun `database should reject duplicate relationship IDs`() {
        val original = relationship()
        persistenceAdapter.save(original)

        assertThatThrownBy {
            insertRelationship(original.id, original.acquisitionSourceId, original.uniqueDefinitionId)
        }.isInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun `database should reject unknown acquisition source references`() {
        val uniqueId = createPersistedUnique().id

        assertThatThrownBy {
            insertRelationship(DropRelationshipId.generate(), AcquisitionSourceId.generate(), uniqueId)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should reject unknown Unique definition references`() {
        val sourceId = createPersistedSource().id

        assertThatThrownBy {
            insertRelationship(DropRelationshipId.generate(), sourceId, UniqueDefinitionId.generate())
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun relationship(): DropRelationship =
        DropRelationship(DropRelationshipId.generate(), createPersistedSource().id, createPersistedUnique().id)

    private fun createPersistedSource(): AcquisitionSource =
        AcquisitionSource(AcquisitionSourceId.generate()).also(sourcePersistenceAdapter::save)

    private fun createPersistedUnique(): UniqueDefinition =
        UniqueDefinition(UniqueDefinitionId.generate()).also(uniqueDefinitionPersistenceAdapter::save)

    private fun insertRelationship(
        id: DropRelationshipId,
        sourceId: AcquisitionSourceId,
        uniqueId: UniqueDefinitionId,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO poe_companion.drop_relationship (id, acquisition_source_id, unique_definition_id)
            VALUES (?, ?, ?)
            """.trimIndent(),
            id.value,
            sourceId.value,
            uniqueId.value,
        )
    }
}
