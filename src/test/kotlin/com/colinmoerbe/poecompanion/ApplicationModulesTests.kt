package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.acquisition.DevelopmentAcquisitionAccess
import com.colinmoerbe.poecompanion.acquisition.attempts.internal.AvailableAttemptsPersistencePort
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionQueryResult
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionReadModelService
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionSourceAssessment
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionSourceBrowseResult
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionSourceDetailResult
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionSourceRelationshipResult
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionSourceSummary
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionUniqueCandidatesResult
import com.colinmoerbe.poecompanion.acquisition.relationship.persistence.DropRelationshipPersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.persistence.AcquisitionSourcePersistenceAdapter
import com.colinmoerbe.poecompanion.build.DevelopmentBuildAccess
import com.colinmoerbe.poecompanion.build.identity.BuildArchetype
import com.colinmoerbe.poecompanion.build.identity.BuildVariantId
import com.colinmoerbe.poecompanion.build.identity.persistence.BuildArchetypePersistenceAdapter
import com.colinmoerbe.poecompanion.build.readiness.RequirementEvaluation
import com.colinmoerbe.poecompanion.build.readiness.RequirementGroupEvaluation
import com.colinmoerbe.poecompanion.build.readiness.UniqueReadiness
import com.colinmoerbe.poecompanion.build.readiness.UniqueReadinessEvaluator
import com.colinmoerbe.poecompanion.build.readiness.UniqueReadinessResult
import com.colinmoerbe.poecompanion.build.readmodel.BuildCatalogEntry
import com.colinmoerbe.poecompanion.build.readmodel.BuildCatalogQueryService
import com.colinmoerbe.poecompanion.build.readmodel.BuildReadinessQueryResult
import com.colinmoerbe.poecompanion.build.readmodel.BuildReadinessService
import com.colinmoerbe.poecompanion.build.requirements.Requirement
import com.colinmoerbe.poecompanion.build.requirements.RequirementGroup
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevision
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevisionActivationService
import com.colinmoerbe.poecompanion.build.revision.internal.BuildVariantRevisionPersistencePort
import com.colinmoerbe.poecompanion.build.revision.persistence.BuildVariantRevisionPersistenceAdapter
import com.colinmoerbe.poecompanion.build.revision.persistence.RequirementEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import com.colinmoerbe.poecompanion.browser.ModuleMetadata as BrowserModuleMetadata

class ApplicationModulesTests {

    private val applicationModules = ApplicationModules.of(PoeCompanionApplication::class.java)

    @Test
    fun discoversExpectedModules() {
        val discoveredModuleIdentifiers =
            applicationModules
                .map { it.identifier.toString() }

        assertThat(discoveredModuleIdentifiers)
            .containsExactlyInAnyOrder(
                "account",
                "browser",
                "acquisition",
                "build",
                "catalog",
                "goal",
                "league",
                "security",
            )
    }

    @Test
    fun verifiesModuleStructure() {
        applicationModules.verify()
    }

    @Test
    fun `acquisition exposes read models and the gated fixture entry point but not persistence`() {
        val acquisition = applicationModules.getModuleByName("acquisition").orElseThrow()

        assertThat(acquisition.namedInterfaces.getByName("readmodel")).isPresent

        val readModelTypes = listOf(
            AcquisitionReadModelService::class.java,
            AcquisitionQueryResult::class.java,
            AcquisitionSourceAssessment::class.java,
            AcquisitionSourceBrowseResult::class.java,
            AcquisitionSourceSummary::class.java,
            AcquisitionSourceRelationshipResult::class.java,
            AcquisitionSourceDetailResult::class.java,
            AcquisitionUniqueCandidatesResult::class.java,
        )
        readModelTypes.forEach { type ->
            assertThat(acquisition.isExposed(type))
                .describedAs("${type.simpleName} should be exposed by Acquisition")
                .isTrue()
        }
        assertThat(acquisition.isExposed(DevelopmentAcquisitionAccess::class.java)).isTrue()
        assertThat(acquisition.isExposed(AcquisitionSourcePersistenceAdapter::class.java)).isFalse()
        assertThat(acquisition.isExposed(DropRelationshipPersistenceAdapter::class.java)).isFalse()
        assertThat(acquisition.isExposed(AvailableAttemptsPersistencePort::class.java)).isFalse()
    }

    @Test
    fun `browser depends on named Acquisition and Build capability APIs without broad module roots`() {
        val allowed = BrowserModuleMetadata::class.java
            .getAnnotation(org.springframework.modulith.ApplicationModule::class.java)
            .allowedDependencies

        assertThat(allowed).containsExactlyInAnyOrder(
            "account",
            "acquisition :: sourceidentity",
            "acquisition :: encounter",
            "acquisition :: knowledge",
            "acquisition :: relationship",
            "acquisition :: probability",
            "acquisition :: attempts",
            "acquisition :: readmodel",
            "build :: identity",
            "build :: revision",
            "build :: requirements",
            "build :: readiness",
            "build :: readmodel",
            "catalog",
            "league",
        )
    }

    @Test
    fun `build exposes capability APIs and gated fixture root but keeps persistence internal`() {
        val build = applicationModules.getModuleByName("build").orElseThrow()

        listOf("identity", "revision", "requirements", "readiness", "readmodel").forEach { namedInterface ->
            assertThat(build.namedInterfaces.getByName(namedInterface))
                .describedAs("Build named interface %s should be available", namedInterface)
                .isPresent
        }

        listOf(
            BuildArchetype::class.java,
            BuildVariantId::class.java,
            BuildVariantRevision::class.java,
            BuildVariantRevisionActivationService::class.java,
            Requirement::class.java,
            RequirementGroup::class.java,
            RequirementEvaluation::class.java,
            RequirementGroupEvaluation::class.java,
            UniqueReadiness::class.java,
            UniqueReadinessEvaluator::class.java,
            UniqueReadinessResult::class.java,
            BuildCatalogEntry::class.java,
            BuildCatalogQueryService::class.java,
            BuildReadinessQueryResult::class.java,
            BuildReadinessService::class.java,
        ).forEach { type ->
            assertThat(build.isExposed(type))
                .describedAs("${type.simpleName} should be exposed by Build")
                .isTrue()
        }

        assertThat(build.isExposed(DevelopmentBuildAccess::class.java)).isTrue()
        assertThat(build.isExposed(BuildVariantRevisionPersistencePort::class.java)).isFalse()
        assertThat(build.isExposed(BuildArchetypePersistenceAdapter::class.java)).isFalse()
        assertThat(build.isExposed(BuildVariantRevisionPersistenceAdapter::class.java)).isFalse()
        assertThat(build.isExposed(RequirementEntity::class.java)).isFalse()
    }
}
