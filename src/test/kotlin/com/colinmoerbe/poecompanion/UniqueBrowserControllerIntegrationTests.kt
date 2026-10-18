package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountEvaluationSelectionRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import javax.sql.DataSource

/** Full HTTP rendering against the real Catalog and Account services and PostgreSQL fixture records. */
@Import(TestcontainersConfiguration::class)
@AutoConfigureMockMvc
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class UniqueBrowserControllerIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val initializer: DevelopmentFixtureInitializer,
    @Autowired private val ownership: ManualOwnershipService,
    @Autowired private val catalog: UniqueDefinitionPersistenceAdapter,
    @Autowired private val selectionRepository: SpringDataAccountEvaluationSelectionRepository,
    @Autowired dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private val ids = DevelopmentFixtureIds

    @BeforeEach
    fun beforeEach() = clearDatabase()

    @AfterEach
    fun afterEach() = clearDatabase()

    @Test
    fun `collection shows every known Unique with quantities from selected account`() {
        initializer.initialize()

        mockMvc.perform(get("/uniques").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(view().name("uniques/list"))
            .andExpect(content().string(containsString(">Unique A</a>")))
            .andExpect(content().string(containsString(">Unique B</a>")))
            .andExpect(content().string(containsString(">Unique C</a>")))
            .andExpect(content().string(containsString("2</strong> owned")))
            .andExpect(content().string(containsString("1</strong> missing")))
            .andExpect(content().string(containsString("Synthetic development item")))
    }

    @Test
    fun `owned and missing filters use recorded quantities rather than fixture names`() {
        initializer.initialize()

        mockMvc.perform(get("/uniques").param("filter", "owned").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString(">Unique A</a>")))
            .andExpect(content().string(containsString(">Unique B</a>")))
            .andExpect(content().string(not(containsString(">Unique C</a>"))))

        mockMvc.perform(get("/uniques").param("filter", "missing").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString(">Unique C</a>")))
            .andExpect(content().string(not(containsString(">Unique A</a>"))))
            .andExpect(content().string(not(containsString(">Unique B</a>"))))
    }

    @Test
    fun `Unique C detail changes from missing to owned after an ownership command`() {
        initializer.initialize()
        val uri = "/uniques/${ids.uniqueC.value}"

        mockMvc.perform(get(uri).with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(view().name("uniques/detail"))
            .andExpect(content().string(containsString("Missing · 0 copies")))

        ownership.setOwnedQuantity(ids.account, ids.uniqueC, 1)

        mockMvc.perform(get(uri).with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Owned · <span>1</span> copies")))
    }

    @Test
    fun `without an account selection ownership is unknown not zero`() {
        initializer.initialize()
        selectionRepository.deleteAllInBatch()

        mockMvc.perform(get("/uniques").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("ownership is unknown")))
            .andExpect(content().string(containsString("Ownership unknown")))
            .andExpect(content().string(not(containsString("Missing · 0"))))

        mockMvc.perform(get("/uniques/${ids.uniqueC.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Unknown — select an account")))

        mockMvc.perform(get("/uniques").param("filter", "missing").with(user("test-user")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `empty catalog is explained without automatically installing development data`() {
        assertThat(catalog.findAll()).isEmpty()

        mockMvc.perform(get("/uniques").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("The catalog is empty")))
    }

    @Test
    fun `non-fixture catalog identities are browsable without fabricated display names`() {
        initializer.initialize()
        val extra = UniqueDefinitionId.generate()
        catalog.save(UniqueDefinition(extra))

        mockMvc.perform(get("/uniques").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Unique (name unavailable)")))
            .andExpect(content().string(containsString(extra.value.toString())))

        mockMvc.perform(get("/uniques/${extra.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Canonical display metadata has not been imported")))
    }

    @Test
    fun `bad IDs and unknown filters return proper HTTP errors`() {
        initializer.initialize()

        mockMvc.perform(get("/uniques/${UniqueDefinitionId.generate().value}").with(user("test-user")))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/uniques/invalid-id").with(user("test-user")))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get("/uniques").param("filter", "invalid").with(user("test-user")))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get("/uniques").param("filter", "").with(user("test-user")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `anonymous visitors are redirected to login`() {
        mockMvc.perform(get("/uniques"))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("/login")))

        mockMvc.perform(get("/uniques/${ids.uniqueC.value}"))
            .andExpect(status().is3xxRedirection)
    }

    private fun clearDatabase() {
        jdbc.execute(
            """
            TRUNCATE TABLE
                poe_companion.boss_definition,
                poe_companion.unique_definition,
                poe_companion.league_definition,
                poe_companion.build_archetype,
                poe_companion.acquisition_source
            CASCADE
            """.trimIndent(),
        )
    }
}
