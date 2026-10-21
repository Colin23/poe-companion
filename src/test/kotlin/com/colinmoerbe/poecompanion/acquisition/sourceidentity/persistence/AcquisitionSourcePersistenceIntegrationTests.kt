package com.colinmoerbe.poecompanion.acquisition.sourceidentity.persistence

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSource
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
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
 * Verifies that canonical acquisition source identity is preserved by PostgreSQL persistence.
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
internal class AcquisitionSourcePersistenceIntegrationTests(
    @Autowired private val persistenceAdapter: AcquisitionSourcePersistenceAdapter,
    @Autowired private val springDataRepository: SpringDataAcquisitionSourceRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearAcquisitionSources() {
        springDataRepository.deleteAllInBatch()
    }

    @Test
    fun `persisted acquisition source should retain its application-owned identity`() {
        val source = AcquisitionSource(AcquisitionSourceId.generate())

        persistenceAdapter.save(source)

        assertThat(persistenceAdapter.findById(source.id)?.id).isEqualTo(source.id)
    }

    @Test
    fun `unrecorded source identity should not resolve to a canonical source`() {
        assertThat(persistenceAdapter.findById(AcquisitionSourceId.generate())).isNull()
    }

    @Test
    fun `distinct source identities should remain independently persisted`() {
        val regularSource = AcquisitionSource(AcquisitionSourceId.generate())
        val uberSource = AcquisitionSource(AcquisitionSourceId.generate())

        persistenceAdapter.save(regularSource)
        persistenceAdapter.save(uberSource)

        assertThat(persistenceAdapter.findById(regularSource.id)?.id).isEqualTo(regularSource.id)
        assertThat(persistenceAdapter.findById(uberSource.id)?.id).isEqualTo(uberSource.id)
        assertThat(springDataRepository.count()).isEqualTo(2L)
    }

    @Test
    fun `database should reject duplicate acquisition source identities`() {
        val sourceId = AcquisitionSourceId.generate()

        jdbcTemplate.update(
            "INSERT INTO poe_companion.acquisition_source (id) VALUES (?)",
            sourceId.value,
        )

        // Bypass JPA so the test exercises the PostgreSQL primary-key constraint directly.
        assertThatThrownBy {
            jdbcTemplate.update(
                "INSERT INTO poe_companion.acquisition_source (id) VALUES (?)",
                sourceId.value,
            )
        }.isInstanceOf(DuplicateKeyException::class.java)
    }
}
