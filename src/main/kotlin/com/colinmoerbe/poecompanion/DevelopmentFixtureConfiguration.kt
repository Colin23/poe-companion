package com.colinmoerbe.poecompanion

import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Startup fixture loading is opt-in; tests can invoke [DevelopmentFixtureInitializer] without either switch.
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
