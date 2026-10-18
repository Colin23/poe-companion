package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.persistence.SpringDataBossDefinitionRepository
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
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * PostgreSQL-backed checks for application-owned BossDefinition identity and curator duplicate-name safeguards.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class BossDefinitionServiceIntegrationTests(
    @Autowired private val service: BossDefinitionService,
    @Autowired private val repository: SpringDataBossDefinitionRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {
    @BeforeEach
    fun beforeTest() {
        repository.deleteAllInBatch()
    }

    @AfterEach
    fun afterTest() {
        repository.deleteAllInBatch()
    }

    @Test
    fun `boss definition should persist and reload with the same identity and name`() {
        val boss = definition(name = "The Elder")

        service.create(boss)

        val restored = service.findById(boss.id)
        assertThat(restored?.id).isEqualTo(boss.id)
        assertThat(restored?.displayName).isEqualTo("The Elder")
        assertThat(repository.count()).isEqualTo(1L)
    }

    @Test
    fun `different bosses with distinct names should have distinct identities`() {
        val elder = definition(name = "The Elder")
        val shaper = definition(name = "The Shaper")

        service.create(elder)
        service.create(shaper)

        assertThat(service.findById(elder.id)?.displayName).isEqualTo("The Elder")
        assertThat(service.findById(shaper.id)?.displayName).isEqualTo("The Shaper")
        assertThat(repository.count()).isEqualTo(2L)
    }

    @Test
    fun `unknown identity should have no persisted definition`() {
        assertThat(service.findById(BossDefinitionId.generate())).isNull()
    }

    @Test
    fun `same identity and exact metadata should be idempotent`() {
        val first = definition(name = "The Elder")
        service.create(first)

        service.create(BossDefinition(first.id, first.displayName))

        assertThat(repository.count()).isEqualTo(1L)
        assertThat(service.findById(first.id)?.displayName).isEqualTo("The Elder")
    }

    @Test
    fun `existing identity must not be rebound to another display name`() {
        val boss = definition(name = "The Elder")
        service.create(boss)

        assertThatThrownBy { service.create(BossDefinition(boss.id, "The Shaper")) }
            .isInstanceOf(BossDefinitionCreationConflictException::class.java)

        assertThat(service.findById(boss.id)?.displayName).isEqualTo("The Elder")
        assertThat(repository.count()).isEqualTo(1L)
    }

    @Test
    fun `distinct identity with same name should require curator review`() {
        service.create(definition(name = "The Elder"))

        assertThatThrownBy { service.create(definition(name = "The Elder")) }
            .isInstanceOf(BossDefinitionCreationConflictException::class.java)
        assertThat(repository.count()).isEqualTo(1L)
    }

    @Test
    fun `leading trailing and repeated whitespace and casing should not bypass name guard`() {
        service.create(definition(name = "The Elder"))

        assertThatThrownBy { service.create(definition(name = "  THE   elder  ")) }
            .isInstanceOf(BossDefinitionCreationConflictException::class.java)
        assertThatThrownBy { service.create(definition(name = "the\tELDER")) }
            .isInstanceOf(BossDefinitionCreationConflictException::class.java)
        assertThat(repository.count()).isEqualTo(1L)
    }

    @Test
    fun `unicode compatibility and unicode whitespace should not bypass name guard`() {
        service.create(definition(name = "The Elder"))

        assertThatThrownBy { service.create(definition(name = "Ｔｈｅ　Ｅｌｄｅｒ")) }
            .isInstanceOf(BossDefinitionCreationConflictException::class.java)
        assertThatThrownBy { service.create(definition(name = "The\u00a0Elder")) }
            .isInstanceOf(BossDefinitionCreationConflictException::class.java)
        assertThat(repository.count()).isEqualTo(1L)
    }

    @Test
    fun `blank boss name must be rejected before persistence`() {
        assertThatThrownBy { definition(name = "  ").also(service::create) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repository.count()).isZero()
    }

    @Test
    fun `database should reject blank names and NULL even when bypassing curator service`() {
        assertThatThrownBy { insertRaw(UUID.randomUUID(), "   ") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRaw(UUID.randomUUID(), null) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should reject whitespace-only names that Kotlin regards as blank`() {
        val whitespaceOnlyNames = listOf("\t", "\n", "\u00a0", "\u2003", "\u001c", " \t\u00a0 ")

        whitespaceOnlyNames.forEach { name ->
            assertThat(name.isBlank()).isTrue()
            assertThatThrownBy { insertRaw(UUID.randomUUID(), name) }
                .isInstanceOf(DataIntegrityViolationException::class.java)
        }

        assertThat(repository.count()).isZero()
    }

    @Test
    fun `database should allow meaningful text surrounded by whitespace`() {
        val id = UUID.randomUUID()
        insertRaw(id, " \tThe Elder\n ")

        assertThat(service.findById(BossDefinitionId(id))?.displayName).isEqualTo(" \tThe Elder\n ")
    }

    @Test
    fun `database primary key should reject different names under same identity`() {
        val boss = definition(name = "The Elder")
        service.create(boss)

        assertThatThrownBy { insertRaw(boss.id.value, "The Shaper") }
            .isInstanceOf(DuplicateKeyException::class.java)
        assertThat(service.findById(boss.id)?.displayName).isEqualTo("The Elder")
    }

    @Test
    fun `concurrent same ID same name creates only one record without conflict`() {
        val boss = definition(name = "The Elder")
        val results = raceCreations(
            { service.create(boss) },
            { service.create(BossDefinition(boss.id, boss.displayName)) },
        )

        assertThat(results.all { it.isSuccess }).isTrue()
        assertThat(repository.count()).isEqualTo(1L)
    }

    @Test
    fun `concurrent same ID different names must never overwrite winning metadata`() {
        val id = BossDefinitionId.generate()
        val results = raceCreations(
            { service.create(BossDefinition(id, "The Elder")) },
            { service.create(BossDefinition(id, "The Shaper")) },
        )

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.exceptionOrNull() is BossDefinitionCreationConflictException }).isEqualTo(1)
        assertThat(repository.count()).isEqualTo(1L)
        assertThat(service.findById(id)?.displayName).isIn("The Elder", "The Shaper")
    }

    private fun definition(name: String): BossDefinition = BossDefinition(BossDefinitionId.generate(), name)

    private fun insertRaw(id: UUID, displayName: String?) {
        jdbcTemplate.update(
            "INSERT INTO poe_companion.boss_definition (id, display_name) VALUES (?, ?)",
            id,
            displayName,
        )
    }

    private fun raceCreations(first: () -> Unit, second: () -> Unit): List<Result<Unit>> {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)

        return try {
            val tasks =
                listOf(first, second).map { operation ->
                    executor.submit(
                        Callable {
                            ready.countDown()
                            check(start.await(10, TimeUnit.SECONDS)) { "Timed out starting competing curator writes" }
                            runCatching(operation)
                        },
                    )
                }
            check(ready.await(10, TimeUnit.SECONDS)) { "Curator workers did not become ready" }
            start.countDown()
            tasks.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }
}
