package com.colinmoerbe.poecompanion.security.internal

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("poe-companion.security")
internal class ApplicationSecurityProperties(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String,
)
