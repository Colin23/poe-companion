package com.colinmoerbe.poecompanion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/** Verify profile/property gating independently of a database and without running a fixture. */
internal class DevelopmentFixtureStartupGatingTests {
    private val contextRunner =
        ApplicationContextRunner().withUserConfiguration(DevelopmentFixtureConfiguration::class.java)

    @Test
    fun `property alone cannot activate the development fixture runner`() {
        contextRunner.withPropertyValues("poe-companion.dev-fixture.enabled=true").run { context ->
            assertThat(context).doesNotHaveBean("developmentFixtureRunner")
        }
    }

    @Test
    fun `dev profile alone cannot activate the development fixture runner`() {
        contextRunner.withPropertyValues("spring.profiles.active=dev").run { context ->
            assertThat(context).doesNotHaveBean("developmentFixtureRunner")
        }
    }

    @Test
    fun `neither switch cannot activate development fixture runner`() {
        contextRunner.run { context ->
            assertThat(context).doesNotHaveBean("developmentFixtureRunner")
        }
    }
}
