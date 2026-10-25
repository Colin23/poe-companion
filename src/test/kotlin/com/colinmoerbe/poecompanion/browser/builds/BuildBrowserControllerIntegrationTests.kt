package com.colinmoerbe.poecompanion.browser.builds

import com.colinmoerbe.poecompanion.DevelopmentFixtureIds
import com.colinmoerbe.poecompanion.DevelopmentFixtureInitializer
import com.colinmoerbe.poecompanion.SecurityTestActors
import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.build.DevelopmentBuildAccess
import com.colinmoerbe.poecompanion.build.identity.BuildArchetype
import com.colinmoerbe.poecompanion.build.identity.BuildArchetypeId
import com.colinmoerbe.poecompanion.build.identity.BuildVariant
import com.colinmoerbe.poecompanion.build.identity.BuildVariantId
import com.colinmoerbe.poecompanion.build.identity.persistence.BuildArchetypePersistenceAdapter
import com.colinmoerbe.poecompanion.build.identity.persistence.BuildVariantPersistenceAdapter
import com.colinmoerbe.poecompanion.build.requirements.Requirement
import com.colinmoerbe.poecompanion.build.requirements.RequirementGroup
import com.colinmoerbe.poecompanion.build.requirements.RequirementImportance
import com.colinmoerbe.poecompanion.build.requirements.RequirementLogic
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevision
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevisionId
import com.colinmoerbe.poecompanion.build.revision.persistence.BuildVariantRevisionPersistenceAdapter
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountEvaluationSelectionRepository
import com.colinmoerbe.poecompanion.security.identity.DevelopmentFixtureActor
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
        "poe-companion.security.user.username=test-user",
        "poe-companion.security.user.password=test-password",
        "poe-companion.security.admin.username=test-admin",
        "poe-companion.security.admin.password=test-admin-password",
    ],
)
@Suppress("LongParameterList")
internal class BuildBrowserControllerIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val initializer: DevelopmentFixtureInitializer,
    @Autowired private val ownership: ManualOwnershipService,
    @Autowired private val fixtureActor: DevelopmentFixtureActor,
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
        assertThat(listPage.totalCount).isEqualTo(4)
        val buildX = listPage.sections.flatMap { it.entries }.single { it.id == ids.variant.value }
        assertThat(buildX.state).isEqualTo(BuildBrowserState.PARTIAL)

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
            .andExpect(content().string(containsString("Missing items")))
            .andExpect(content().string(containsString("Required Uniques")))
            .andExpect(content().string(containsString("Requirements available for version 3.30")))
            .andExpect(content().string(containsString("Missing 1 copy")))
    }

    @Test
    fun `expanded Builds P Q and R render independent readiness and requirements`() {
        initializer.initialize()

        val entries = listPage().sections.flatMap { it.entries }
        assertThat(entries.map { it.displayName })
            .containsExactlyInAnyOrder("Build X", "Build P", "Build Q", "Build R")

        val buildP = detailPage(ids.variantP)
        assertThat(buildP.state).isEqualTo(BuildBrowserState.BLOCKED)
        assertThat(buildP.groups.map { it.logic }).containsExactly("ANY", "ALL")

        val buildQ = detailPage(ids.variantQ)
        assertThat(buildQ.state).isEqualTo(BuildBrowserState.PARTIAL)
        val coreRequirement = buildQ.groups.single { it.importance == "CORE" }.requirements.single()
        assertThat(coreRequirement.requiredQuantity).isEqualTo(2)
        assertThat(coreRequirement.ownedQuantity).isEqualTo(1)

        val buildR = detailPage(ids.variantR)
        assertThat(buildR.state).isEqualTo(BuildBrowserState.UNIQUE_READY)
        assertThat(buildR.hasRequiredUniqueGroups).isFalse()

        listOf(
            ids.variantP to "Any one of the alternatives",
            ids.variantQ to "missing copies",
            ids.variantR to "None is required for primary Unique readiness.",
        ).forEach { (id, marker) ->
            mockMvc.perform(get("/builds/${id.value}").with(user("test-user")))
                .andExpect(status().isOk)
                .andExpect(view().name("builds/detail"))
                .andExpect(content().string(containsString(marker)))
        }
    }

    @Test
    fun `owning C turns Build X unique ready without reloading the fixture`() {
        initializer.initialize()
        fixtureActor.asLegacyUser { ownership.setOwnedQuantity(ids.account, ids.uniqueC, 1) }

        assertThat(detailPage(ids.variant).state).isEqualTo(BuildBrowserState.UNIQUE_READY)
        assertThat(detailPage(ids.variant).groups).allMatch { it.satisfied == true }

        val page = listPage()
        val buildX = page.sections.flatMap { it.entries }.single { it.id == ids.variant.value }
        assertThat(buildX.state).isEqualTo(BuildBrowserState.UNIQUE_READY)
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
        val noRequired = listPage().sections.single { it.category == BuildBrowserListCategory.NO_REQUIRED }
        assertThat(noRequired.entries.map { it.displayName })
            .contains("Upgrades Only", "No Requirements")
        assertThat(noRequired.entries).allMatch { it.state == BuildBrowserState.UNIQUE_READY }
    }

    @Test
    fun `Build list separates no required Uniques from readiness without changing domain states`() {
        initializer.initialize()
        fixtureActor.asLegacyUser { ownership.setOwnedQuantity(ids.account, ids.uniqueC, 1) }

        val categories = listPage().sections.map { it.category }
        assertThat(categories).containsExactly(
            BuildBrowserListCategory.NO_REQUIRED,
            BuildBrowserListCategory.UNIQUE_READY,
            BuildBrowserListCategory.PARTIAL,
            BuildBrowserListCategory.BLOCKED,
        )
        assertThat(listPage().sections.single { it.category == BuildBrowserListCategory.NO_REQUIRED }.entries)
            .anyMatch { it.displayName == "Build R" && it.state == BuildBrowserState.UNIQUE_READY }

        val listHtml = mockMvc.perform(get("/builds").with(user("test-user")))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(listHtml).contains("No required Uniques", "Unique ready", "Partial", "Blocked")
            .doesNotContain("All enabling and core Unique requirements are satisfied.</span>")
    }

    @Test
    fun `optional upgrade groups stay visually distinct and do not imply required failure`() {
        initializer.initialize()

        val html = mockMvc.perform(get("/builds/${ids.variantR.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        assertThat(html).contains("No required Uniques", "Optional upgrades", "Not collected yet")
            .contains("Owning any one of these Uniques completes this group.")
            .doesNotContain("Build variant ID", "Revision verification", "CORE — ALL", "UPGRADE — ANY")
    }

    @Test
    fun `unknown personal ownership shows required quantities without pretending missing copies`() {
        initializer.initialize()
        // Standard is recognized and has compatible requirements, but USER has no personal context there.
        mockMvc.perform(
            post("/environment").with(user("test-user").roles("USER")).with(csrf())
                .param("version", "3.30")
                .param("type", "STANDARD")
                .param("mortality", "SOFTCORE")
                .param("participation", "SSF")
                .param("ruleset", "NORMAL"),
        ).andExpect(status().is3xxRedirection)

        assertThat(detailPage(ids.variant).state).isEqualTo(BuildBrowserState.NO_PERSONAL)
        val html = mockMvc.perform(get("/builds/${ids.variant.value}").with(user("test-user").roles("USER")))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(html).contains("Requirements available for version 3.30")
            .contains("Requires 1 copy")
            .contains("Ownership not evaluated")
            .doesNotContain("Build variant ID")
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
        assertThat(listing.environmentSelected).isFalse()
        assertThat(listing.sections.single().category).isEqualTo(BuildBrowserListCategory.NO_ACCOUNT)
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
        assertThat(page.environmentSelected).isFalse()
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

    @Test
    fun `shared header distinguishes game environment and user on desktop and mobile`() {
        initializer.initialize()

        listOf("test-user" to "USER", "test-admin" to "ADMIN").forEach { (username, role) ->
            val html =
                mockMvc.perform(get("/").with(user(username).roles(role)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

            assertThat(html)
                .contains("aria-label=\"Primary\"")
                .contains("aria-label=\"Mobile navigation\"")
                .contains("<details class=\"mobile-navigation\">")
                .contains("src=\"/js/mobile-navigation.js\"")
                .contains("<summary class=\"mobile-menu-toggle\">")
                .contains("Game environment:")
                .contains("href=\"/environment\"")
                .contains("3.30 SC SSF Development SSF league")
                .contains("Signed in as <strong>$username</strong>")
                .contains("action=\"/logout\"")
                .contains("name=\"_csrf\"")
        }

        val environmentPage =
            mockMvc.perform(get("/environment").with(user("test-user").roles("USER")))
                .andExpect(status().isOk)
                .andReturn().response.contentAsString
        assertThat(environmentPage)
            .contains("class=\"environment-switch is-active\"")
            .contains("aria-current=\"page\"")

        selectionRepository.deleteAll()

        val withoutSelection =
            mockMvc.perform(get("/").with(user("test-user").roles("USER")))
                .andExpect(status().isOk)
                .andReturn().response.contentAsString

        assertThat(withoutSelection)
            .contains("Not selected")
            .contains("aria-label=\"Select game environment\"")
    }

    @Test
    fun `mobile menu dismissal script is served for outside clicks and Escape`() {
        mockMvc.perform(get("/js/mobile-navigation.js").with(user("test-user").roles("USER")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("document.addEventListener(\"click\"")))
            .andExpect(content().string(containsString("document.addEventListener(\"focusin\"")))
            .andExpect(content().string(containsString("event.key === \"Escape\"")))
    }

    @Test
    fun `home explains each player journey without promising complete drop knowledge`() {
        val html =
            mockMvc.perform(get("/").with(user("test-user").roles("USER")))
                .andExpect(status().isOk)
                .andReturn().response.contentAsString

        assertThat(html)
            .contains("Explore Builds")
            .contains("Compare a Build")
            .contains("Unique Collection")
            .contains("switching leagues does not overwrite your progress")
            .contains("Acquisition Sources")
            .contains("missing knowledge does not mean a drop is impossible")
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
            SecurityTestActors.asAdmin { fixtureBuilds.createAndActivate(archetype, variant, revision) }
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
