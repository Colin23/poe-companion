package com.colinmoerbe.poecompanion

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
}
