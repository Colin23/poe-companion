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
class UniqueDefinitionRepositoryIntegrationTests(
    @Autowired private val repository: UniqueDefinitionRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearUniqueDefinitions() {
        jdbcTemplate.update("DELETE FROM poe_companion.unique_definition")
    }

    @Test
    fun `persisted unique definition should retain its application-owned identity`() {
        val original = UniqueDefinition(UniqueDefinitionId.generate())

        repository.save(original)
        val persisted = repository.findById(original.id)

        assertThat(persisted?.id).isEqualTo(original.id)
    }

    @Test
    fun `database should reject duplicate unique definition identities`() {
        val id = UniqueDefinitionId.generate()

        jdbcTemplate.update(
            "INSERT INTO poe_companion.unique_definition (id) VALUES (?)",
            id.value,
        )

        assertThatThrownBy {
            jdbcTemplate.update(
                "INSERT INTO poe_companion.unique_definition (id) VALUES (?)",
                id.value,
            )
        }.isInstanceOf(DuplicateKeyException::class.java)
    }
}
