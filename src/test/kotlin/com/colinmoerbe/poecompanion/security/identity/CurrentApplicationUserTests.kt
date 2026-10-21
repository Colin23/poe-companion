package com.colinmoerbe.poecompanion.security.identity

import com.colinmoerbe.poecompanion.security.internal.ApplicationSecurityProperties
import com.colinmoerbe.poecompanion.security.internal.ConfiguredCredentials
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class CurrentApplicationUserTests {
    private val currentUser = CurrentApplicationUser(
        ApplicationSecurityProperties(
            user = ConfiguredCredentials("test-user", "test-password"),
            admin = ConfiguredCredentials("test-admin", "test-admin-password"),
        ),
    )

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `authenticated configured user maps to stable local UUID`() {
        authenticate("test-user", "ROLE_USER")

        assertThat(currentUser.requireId()).isEqualTo(ConfiguredApplicationUsers.USER)
    }

    @Test
    fun `missing authentication cannot access a personal context`() {
        assertThatThrownBy { currentUser.requireId() }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `another principal cannot inherit the configured user identity`() {
        authenticate("someone-else", "ROLE_USER")

        assertThatThrownBy { currentUser.requireId() }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `authenticated admin maps to the separate stable local UUID`() {
        authenticate("test-admin", "ROLE_ADMIN")

        assertThat(currentUser.requireId()).isEqualTo(ConfiguredApplicationUsers.ADMIN)
    }

    @Test
    fun `an admin username without the ADMIN role cannot access admin identity`() {
        authenticate("test-admin", "ROLE_USER")

        assertThatThrownBy { currentUser.requireId() }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `a USER username with the ADMIN role cannot impersonate admin`() {
        authenticate("test-user", "ROLE_ADMIN")

        assertThatThrownBy { currentUser.requireId() }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `principals with both roles cannot resolve either configured identity`() {
        listOf("test-user", "test-admin").forEach { username ->
            authenticate(username, "ROLE_USER", "ROLE_ADMIN")

            assertThatThrownBy { currentUser.requireId() }
                .isInstanceOf(AccessDeniedException::class.java)
        }
    }

    private fun authenticate(username: String, vararg authorities: String) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            username,
            null,
            authorities.map { SimpleGrantedAuthority(it) },
        )
    }
}
