package com.colinmoerbe.poecompanion.security.internal

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
    fun `configured USER and ADMIN passwords must differ`() {
        assertThatThrownBy {
            ApplicationSecurityProperties(
                user = ConfiguredCredentials("test-user", "same-password"),
                admin = ConfiguredCredentials("test-admin", "same-password"),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("USER and ADMIN passwords must differ")
    }
}
