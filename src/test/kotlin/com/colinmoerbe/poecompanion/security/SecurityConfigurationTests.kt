package com.colinmoerbe.poecompanion.security

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@Import(TestcontainersConfiguration::class)
@AutoConfigureMockMvc
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
class SecurityConfigurationTests(@Autowired private val mockMvc: MockMvc) {
    @Test
    fun allowsAnonymousHealthChecks() {
        mockMvc
            .perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun redirectsAnonymousApplicationRequestsToLogin() {
        mockMvc
            .perform(get("/"))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("/login")))
    }

    @Test
    fun acceptsConfiguredApplicationCredentials() {
        mockMvc
            .perform(
                formLogin()
                    .user("test-user")
                    .password("test-password"),
            ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
            .andExpect(authenticated().withUsername("test-user"))
    }

    @Test
    fun rendersHomePageForAuthenticatedUser() {
        mockMvc
            .perform(
                get("/")
                    .with(user("test-user")),
            ).andExpect(status().isOk)
            .andExpect(view().name("index"))
            .andExpect(content().string(containsString("PoE Companion")))
            .andExpect(content().string(containsString("test-user")))
            .andExpect(content().string(containsString("Application bootstrap operational")))
    }

    @Test
    fun rejectsStateChangesWithoutCsrfToken() {
        mockMvc
            .perform(
                post("/logout")
                    .with(user("test-user")),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun logsOutWithCsrfToken() {
        mockMvc
            .perform(
                post("/logout")
                    .with(user("test-user"))
                    .with(csrf()),
            ).andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/login?logout"))
            .andExpect(unauthenticated())
    }
}
