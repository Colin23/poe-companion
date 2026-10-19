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
import com.colinmoerbe.poecompanion.browser.ModuleMetadata as BrowserModuleMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

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
    fun `browser names its acquisition dependencies rather than depending on its module root`() {
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
            "build",
            "catalog",
            "league",
        )
    }
}
