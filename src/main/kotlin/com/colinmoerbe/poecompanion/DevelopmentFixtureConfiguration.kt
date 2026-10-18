package com.colinmoerbe.poecompanion

import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Automatic startup loading requires both the dev profile and the opt-in property.
 * Tests enable the property without the dev profile to invoke [DevelopmentFixtureInitializer] explicitly.
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
@ConditionalOnProperty(prefix = "poe-companion.dev-fixture", name = ["enabled"], havingValue = "true")
internal class DevelopmentFixtureConfiguration {
    @Bean
    fun developmentFixtureRunner(initializer: DevelopmentFixtureInitializer): ApplicationRunner = ApplicationRunner {
        initializer.initialize()
    }
}
