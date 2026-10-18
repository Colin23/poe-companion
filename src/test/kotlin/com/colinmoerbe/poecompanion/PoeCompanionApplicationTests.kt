package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.acquisition.DevelopmentAcquisitionAccess
import com.colinmoerbe.poecompanion.build.DevelopmentBuildAccess
import com.colinmoerbe.poecompanion.catalog.DevelopmentCatalogAccess
import com.colinmoerbe.poecompanion.league.DevelopmentLeagueAccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
class PoeCompanionApplicationTests(@Autowired private val context: ApplicationContext) {
    @Test
    fun contextLoads() = Unit

    @Test
    fun developmentFixtureComponentsAreAbsentByDefault() {
        assertThat(context.getBeansOfType(DevelopmentFixtureInitializer::class.java)).isEmpty()
        assertThat(context.getBeansOfType(DevelopmentCatalogAccess::class.java)).isEmpty()
        assertThat(context.getBeansOfType(DevelopmentLeagueAccess::class.java)).isEmpty()
        assertThat(context.getBeansOfType(DevelopmentBuildAccess::class.java)).isEmpty()
        assertThat(context.getBeansOfType(DevelopmentAcquisitionAccess::class.java)).isEmpty()
        assertThat(context.containsBean("developmentFixtureRunner")).isFalse()
    }
}
