package com.colinmoerbe.poecompanion.catalog.persistence

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.catalog.ExternalIdentity
import com.colinmoerbe.poecompanion.catalog.ExternalProvider
import com.colinmoerbe.poecompanion.catalog.ExternalProviderKey
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Verifies the PostgreSQL persistence contract for external identity mappings.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class ExternalIdentityPersistenceIntegrationTests(
    @Autowired private val persistenceAdapter: ExternalIdentityPersistenceAdapter,
    @Autowired private val springDataRepository: SpringDataExternalIdentityRepository,
    @Autowired private val uniqueDefinitionPersistenceAdapter: UniqueDefinitionPersistenceAdapter,
    @Autowired private val uniqueDefinitionRepository: SpringDataUniqueDefinitionRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearCatalogIdentityData() {
        springDataRepository.deleteAllInBatch()
        uniqueDefinitionRepository.deleteAllInBatch()
    }

    @Test
    fun `persisted external identity should retain its provider mapping`() {
        val uniqueDefinition = UniqueDefinition(UniqueDefinitionId.generate())
        val externalIdentity =
            ExternalIdentity(
                provider = ExternalProvider.POE_WIKI,
                providerKey = ExternalProviderKey("unique-items/123"),
                uniqueDefinitionId = uniqueDefinition.id,
            )

        uniqueDefinitionPersistenceAdapter.save(uniqueDefinition)
        persistenceAdapter.save(externalIdentity)
        val persisted =
            persistenceAdapter.findByProviderAndProviderKey(
                externalIdentity.provider,
                externalIdentity.providerKey,
            )

        assertThat(persisted).isEqualTo(externalIdentity)
    }

    @Test
    fun `multiple provider identities may map to the same unique definition`() {
        val uniqueDefinition = UniqueDefinition(UniqueDefinitionId.generate())
        uniqueDefinitionPersistenceAdapter.save(uniqueDefinition)

        val wikiIdentity =
            ExternalIdentity(
                provider = ExternalProvider.POE_WIKI,
                providerKey = ExternalProviderKey("wiki-key"),
                uniqueDefinitionId = uniqueDefinition.id,
            )
        val repoeIdentity =
            ExternalIdentity(
                provider = ExternalProvider.REPOE,
                providerKey = ExternalProviderKey("repoe-key"),
                uniqueDefinitionId = uniqueDefinition.id,
            )

        persistenceAdapter.save(wikiIdentity)
        persistenceAdapter.save(repoeIdentity)

        assertThat(
            persistenceAdapter.findByProviderAndProviderKey(
                wikiIdentity.provider,
                wikiIdentity.providerKey,
            ),
        ).isEqualTo(wikiIdentity)
        assertThat(
            persistenceAdapter.findByProviderAndProviderKey(
                repoeIdentity.provider,
                repoeIdentity.providerKey,
            ),
        ).isEqualTo(repoeIdentity)
    }

    @Test
    fun `database should reject one provider key mapping to two unique definitions`() {
        val firstUniqueDefinition = UniqueDefinition(UniqueDefinitionId.generate())
        val secondUniqueDefinition = UniqueDefinition(UniqueDefinitionId.generate())
        uniqueDefinitionPersistenceAdapter.save(firstUniqueDefinition)
        uniqueDefinitionPersistenceAdapter.save(secondUniqueDefinition)

        jdbcTemplate.update(
            """
            INSERT INTO poe_companion.external_identity (provider, provider_key, unique_definition_id)
            VALUES (?, ?, ?)
            """.trimIndent(),
            ExternalProvider.POE_WIKI.name,
            "shared-key",
            firstUniqueDefinition.id.value,
        )

        // Bypass JPA so the test exercises the PostgreSQL composite primary key directly.
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO poe_companion.external_identity (provider, provider_key, unique_definition_id)
                VALUES (?, ?, ?)
                """.trimIndent(),
                ExternalProvider.POE_WIKI.name,
                "shared-key",
                secondUniqueDefinition.id.value,
            )
        }.isInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun `database should reject mappings to unknown unique definitions`() {
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO poe_companion.external_identity (provider, provider_key, unique_definition_id)
                VALUES (?, ?, ?)
                """.trimIndent(),
                ExternalProvider.POE_WIKI.name,
                "missing-target",
                UniqueDefinitionId.generate().value,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should reject unsupported providers`() {
        val uniqueDefinition = UniqueDefinition(UniqueDefinitionId.generate())
        uniqueDefinitionPersistenceAdapter.save(uniqueDefinition)

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO poe_companion.external_identity (provider, provider_key, unique_definition_id)
                VALUES (?, ?, ?)
                """.trimIndent(),
                "ASDF",
                "provider-key",
                uniqueDefinition.id.value,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
