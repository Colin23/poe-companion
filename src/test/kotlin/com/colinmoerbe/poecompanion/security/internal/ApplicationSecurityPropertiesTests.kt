package com.colinmoerbe.poecompanion.security.internal

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** Misconfigured dual login must fail at startup rather than silently share an identity. */
internal class ApplicationSecurityPropertiesTests {
    @Test
    fun `configured USER and ADMIN usernames must differ`() {
        assertThatThrownBy {
            ApplicationSecurityProperties(
                user = ConfiguredCredentials("same-login", "user-password"),
                admin = ConfiguredCredentials("same-login", "admin-password"),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("USER and ADMIN usernames must differ")
    }

    @Test
    fun `configured USER and ADMIN may share a password when usernames differ`() {
        val properties = ApplicationSecurityProperties(
            user = ConfiguredCredentials("test-user", "same-password"),
            admin = ConfiguredCredentials("test-admin", "same-password"),
        )

        assertThat(properties.user.username).isNotEqualTo(properties.admin.username)
        assertThat(properties.user.password).isEqualTo(properties.admin.password)
    }
}
