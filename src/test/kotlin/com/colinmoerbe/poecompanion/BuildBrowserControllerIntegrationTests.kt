package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.build.BuildArchetype
import com.colinmoerbe.poecompanion.build.BuildArchetypeId
import com.colinmoerbe.poecompanion.build.BuildVariant
import com.colinmoerbe.poecompanion.build.BuildVariantId
import com.colinmoerbe.poecompanion.build.BuildVariantRevision
import com.colinmoerbe.poecompanion.build.BuildVariantRevisionId
import com.colinmoerbe.poecompanion.build.DevelopmentBuildAccess
import com.colinmoerbe.poecompanion.build.Requirement
import com.colinmoerbe.poecompanion.build.RequirementGroup
import com.colinmoerbe.poecompanion.build.RequirementImportance
import com.colinmoerbe.poecompanion.build.RequirementLogic
import com.colinmoerbe.poecompanion.build.persistence.BuildArchetypePersistenceAdapter
import com.colinmoerbe.poecompanion.build.persistence.BuildVariantPersistenceAdapter
import com.colinmoerbe.poecompanion.build.persistence.BuildVariantRevisionPersistenceAdapter
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountEvaluationSelectionRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
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

/** End-to-end server-rendered Builds browser against real Build, Account and Catalog persistence. */
@Import(TestcontainersConfiguration::class)
@AutoConfigureMockMvc
@SpringBootTest(
    properties = [
        "poe-companion.dev-fixture.enabled=true",
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
@Suppress("LongParameterList")
internal class BuildBrowserControllerIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val initializer: DevelopmentFixtureInitializer,
    @Autowired private val ownership: ManualOwnershipService,
    @Autowired private val selectionRepository: SpringDataAccountEvaluationSelectionRepository,
    @Autowired private val fixtureBuilds: DevelopmentBuildAccess,
    @Autowired private val archetypes: BuildArchetypePersistenceAdapter,
    @Autowired private val variants: BuildVariantPersistenceAdapter,
    @Autowired private val revisions: BuildVariantRevisionPersistenceAdapter,
    @Autowired private val catalog: UniqueDefinitionPersistenceAdapter,
    @Autowired dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private val ids = DevelopmentFixtureIds

    @BeforeEach
    fun beforeEach() = clearDatabase()

    @AfterEach
    fun afterEach() = clearDatabase()

    @Test
    fun `canonical Build X appears as partial and explains A B C ownership`() {
        initializer.initialize()

        val listing = mockMvc.perform(get("/builds").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(view().name("builds/list"))
            .andExpect(content().string(containsString(">Build X</a>")))
            .andExpect(content().string(containsString("Partial")))
            .andReturn()
        val listPage = listing.modelAndView!!.model["page"] as BuildBrowserListPage
        assertThat(listPage.totalCount).isEqualTo(1)
        assertThat(listPage.sections.single().state).isEqualTo(BuildBrowserState.PARTIAL)

        val detail = detailPage(ids.variant)
        assertThat(detail.state).isEqualTo(BuildBrowserState.PARTIAL)
        assertThat(detail.compatibilityVersion).isEqualTo("3.30")
        assertThat(detail.groups.map { it.importance }).containsExactly("ENABLING", "CORE")
        assertThat(detail.groups.map { it.logic }).containsExactly("ALL", "ALL")
        assertThat(detail.groups.map { it.satisfied }).containsExactly(true, false)
        assertThat(detail.groups[0].requirements.single().ownedQuantity).isEqualTo(1)
        assertThat(detail.groups[1].requirements.map { it.ownedQuantity }).containsExactly(1, 0)
        assertThat(detail.groups[1].requirements.map { it.displayName }).containsExactly("Unique B", "Unique C")

        mockMvc.perform(get("/builds/${ids.variant.value}").with(user("test-user")))
            .andExpect(content().string(containsString("Synthetic development item")))
            .andExpect(content().string(containsString("/uniques/${ids.uniqueC.value}")))
            .andExpect(content().string(containsString("Group incomplete")))
    }

    @Test
    fun `owning C turns Build X unique ready without reloading the fixture`() {
        initializer.initialize()
        ownership.setOwnedQuantity(ids.account, ids.uniqueC, 1)

        assertThat(detailPage(ids.variant).state).isEqualTo(BuildBrowserState.UNIQUE_READY)
        assertThat(detailPage(ids.variant).groups).allMatch { it.satisfied }

        val page = listPage()
        assertThat(page.sections.single().state).isEqualTo(BuildBrowserState.UNIQUE_READY)
    }

    @Test
    fun `unsatisfied enabling group is blocked while ANY alternatives remain independent`() {
        initializer.initialize()
        val blocked = createBuild(
            "Blocked Build",
            listOf(group(RequirementImportance.ENABLING, RequirementLogic.ALL, Requirement(ids.uniqueC, 1))),
        )
        val any = createBuild(
            "Any Build",
            listOf(
                group(
                    RequirementImportance.CORE,
                    RequirementLogic.ANY,
                    Requirement(ids.uniqueA, 1),
                    Requirement(ids.uniqueC, 1),
                ),
            ),
        )

        assertThat(detailPage(blocked).state).isEqualTo(BuildBrowserState.BLOCKED)
        val anyDetail = detailPage(any)
        assertThat(anyDetail.state).isEqualTo(BuildBrowserState.UNIQUE_READY)
        assertThat(anyDetail.groups.single().satisfied).isTrue()
        assertThat(anyDetail.groups.single().requirements.map { it.satisfied }).containsExactly(true, false)
    }

    @Test
    fun `quantity and optional upgrades do not invent a readiness percentage`() {
        initializer.initialize()
        val quantity = createBuild(
            "Two Copies",
            listOf(group(RequirementImportance.CORE, RequirementLogic.ALL, Requirement(ids.uniqueA, 2))),
        )
        val upgradesOnly = createBuild(
            "Upgrades Only",
            listOf(group(RequirementImportance.UPGRADE, RequirementLogic.ALL, Requirement(ids.uniqueC, 1))),
        )
        val empty = createBuild("No Requirements", emptyList())

        val missingCopies = detailPage(quantity)
        assertThat(missingCopies.state).isEqualTo(BuildBrowserState.PARTIAL)
        assertThat(missingCopies.groups.single().requirements.single().requiredQuantity).isEqualTo(2)
        assertThat(missingCopies.groups.single().requirements.single().ownedQuantity).isEqualTo(1)

        val optional = detailPage(upgradesOnly)
        assertThat(optional.state).isEqualTo(BuildBrowserState.UNIQUE_READY)
        assertThat(optional.hasRequiredUniqueGroups).isFalse()
        assertThat(optional.groups.single().satisfied).isFalse()

        assertThat(detailPage(empty).hasRequiredUniqueGroups).isFalse()
        mockMvc.perform(get("/builds/${empty.value}").with(user("test-user")))
            .andExpect(content().string(containsString("No required Uniques")))
        assertThat(listPage().sections.single { it.state == BuildBrowserState.UNIQUE_READY }.entries)
            .anyMatch { it.explanation == "No required Uniques" }
    }

    @Test
    fun `missing account and incompatible or inactive revisions are not classified as blocked`() {
        initializer.initialize()
        val otherVersion = createBuild("Old Build", emptyList(), CompatibilityVersion(3, 29))
        val draft = createBuild("Draft Build", emptyList(), activate = false)
        assertThat(detailPage(otherVersion).state).isEqualTo(BuildBrowserState.NO_ACTIVE_REVISION)
        assertThat(detailPage(draft).state).isEqualTo(BuildBrowserState.NO_ACTIVE_REVISION)
        assertThat(listPage().sections.any { it.state == BuildBrowserState.NO_ACTIVE_REVISION }).isTrue()

        selectionRepository.deleteAllInBatch()
        assertThat(detailPage(ids.variant).state).isEqualTo(BuildBrowserState.NO_ACCOUNT)
        assertThat(detailPage(ids.variant).hasRequiredUniqueGroups).isNull()
        val listing = listPage()
        assertThat(listing.accountSelected).isFalse()
        assertThat(listing.sections.single().state).isEqualTo(BuildBrowserState.NO_ACCOUNT)
    }

    @Test
    fun `non fixture Unique references never receive made up names`() {
        initializer.initialize()
        val extra = UniqueDefinitionId.generate()
        catalog.save(UniqueDefinition(extra))
        val build = createBuild(
            "Unknown Display Name",
            listOf(group(RequirementImportance.CORE, RequirementLogic.ALL, Requirement(extra, 1))),
        )
        val requirement = detailPage(build).groups.single().requirements.single()
        assertThat(requirement.displayName).isEqualTo("Unique (name unavailable)")
        assertThat(requirement.syntheticLabel).isFalse()
        assertThat(requirement.catalogKnown).isTrue()
    }

    @Test
    fun `empty catalogue and invalid identities have explicit HTTP states`() {
        val page = listPage()
        assertThat(page.totalCount).isZero()
        assertThat(page.accountSelected).isFalse()
        mockMvc.perform(get("/builds").with(user("test-user")))
            .andExpect(content().string(containsString("No Build variants are known yet")))

        mockMvc.perform(get("/builds/${BuildVariantId.generate().value}").with(user("test-user")))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/builds/not-a-uuid").with(user("test-user")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `shared navigation renders on both sections and anonymous users must log in`() {
        initializer.initialize()
        listOf("/", "/uniques", "/builds", "/builds/${ids.variant.value}").forEach { uri ->
            mockMvc.perform(get(uri).with(user("test-user")))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("aria-label=\"Primary\"")))
                .andExpect(content().string(containsString(">Uniques</a>")))
                .andExpect(content().string(containsString(">Builds</a>")))
                .andExpect(content().string(containsString("action=\"/logout\"")))
        }
        mockMvc.perform(get("/builds"))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("/login")))
        mockMvc.perform(get("/builds/${ids.variant.value}"))
            .andExpect(status().is3xxRedirection)
    }

    private fun group(
        importance: RequirementImportance,
        logic: RequirementLogic,
        vararg requirements: Requirement,
    ): RequirementGroup = RequirementGroup(importance, logic, requirements.toList())

    private fun createBuild(
        name: String,
        groups: List<RequirementGroup>,
        version: CompatibilityVersion = CompatibilityVersion(3, 30),
        activate: Boolean = true,
    ): BuildVariantId {
        val archetype = BuildArchetype(BuildArchetypeId.generate(), name)
        val variant = BuildVariant(BuildVariantId.generate(), archetype.id)
        val revision = BuildVariantRevision(BuildVariantRevisionId.generate(), variant.id, version)
        revision.updateRequirementGroups(groups)
        if (activate) {
            fixtureBuilds.createAndActivate(archetype, variant, revision)
        } else {
            archetypes.save(archetype)
            variants.save(variant)
            revisions.saveDraftSnapshot(revision)
        }
        return variant.id
    }

    private fun detailPage(id: BuildVariantId): BuildBrowserDetailPage {
        val response = mockMvc.perform(get("/builds/${id.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(view().name("builds/detail"))
            .andReturn()
        return response.modelAndView!!.model["page"] as BuildBrowserDetailPage
    }

    private fun listPage(): BuildBrowserListPage {
        val response = mockMvc.perform(get("/builds").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(view().name("builds/list"))
            .andReturn()
        return response.modelAndView!!.model["page"] as BuildBrowserListPage
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
