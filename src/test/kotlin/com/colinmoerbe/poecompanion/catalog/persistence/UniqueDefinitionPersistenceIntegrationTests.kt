package com.colinmoerbe.poecompanion.catalog.persistence

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Verifies the PostgreSQL persistence contract for catalog unique definitions.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class UniqueDefinitionPersistenceIntegrationTests(
    @Autowired private val persistenceAdapter: UniqueDefinitionPersistenceAdapter,
    @Autowired private val springDataRepository: SpringDataUniqueDefinitionRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearUniqueDefinitions() {
        springDataRepository.deleteAllInBatch()
    }

    @Test
    fun `persisted unique definition should retain its application-owned identity`() {
        val original = UniqueDefinition(UniqueDefinitionId.generate())

        persistenceAdapter.save(original)
        val persisted = persistenceAdapter.findById(original.id)

        assertThat(persisted?.id).isEqualTo(original.id)
    }

    @Test
    fun `database should reject duplicate unique definition identities`() {
        val id = UniqueDefinitionId.generate()

        jdbcTemplate.update(
            "INSERT INTO poe_companion.unique_definition (id) VALUES (?)",
            id.value,
        )

        // Bypass JPA so the test exercises the PostgreSQL primary-key constraint directly.
        assertThatThrownBy {
            jdbcTemplate.update(
                "INSERT INTO poe_companion.unique_definition (id) VALUES (?)",
                id.value,
            )
        }.isInstanceOf(DuplicateKeyException::class.java)
    }
}
