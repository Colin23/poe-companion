package com.colinmoerbe.poecompanion.security.internal

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** Two explicitly configured, temporary V0.1 application logins. */
@Validated
@ConfigurationProperties("poe-companion.security")
internal class ApplicationSecurityProperties(
    @field:Valid val user: ConfiguredCredentials,
    @field:Valid val admin: ConfiguredCredentials,
) {
    init {
        require(user.username != admin.username) { "USER and ADMIN usernames must differ" }
    }
}

internal class ConfiguredCredentials(@field:NotBlank val username: String, @field:NotBlank val password: String)
