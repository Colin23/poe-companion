package com.colinmoerbe.poecompanion.security.identity

import com.colinmoerbe.poecompanion.security.internal.ApplicationSecurityProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class CurrentApplicationUserTests {
    private val currentUser = CurrentApplicationUser(ApplicationSecurityProperties("test-user", "test-password"))

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `authenticated configured user maps to stable local UUID`() {
        authenticate("test-user")

        assertThat(currentUser.requireId()).isEqualTo(ConfiguredApplicationUsers.USER)
    }

    @Test
    fun `missing authentication cannot access a personal context`() {
        assertThatThrownBy { currentUser.requireId() }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `another principal cannot inherit the configured user identity`() {
        authenticate("someone-else")

        assertThatThrownBy { currentUser.requireId() }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    private fun authenticate(username: String) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            username,
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
    }
}
