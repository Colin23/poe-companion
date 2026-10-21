package com.colinmoerbe.poecompanion.security.internal

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApplicationSecurityProperties::class)
internal class SecurityConfiguration {
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun userDetailsService(
        properties: ApplicationSecurityProperties,
        passwordEncoder: PasswordEncoder,
    ): UserDetailsService {
        val user = User
            .withUsername(properties.user.username)
            .password(passwordEncoder.encode(properties.user.password))
            .roles("USER")
            .build()
        val admin = User
            .withUsername(properties.admin.username)
            .password(passwordEncoder.encode(properties.admin.password))
            .roles("ADMIN")
            .build()

        return InMemoryUserDetailsManager(user, admin)
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.authorizeHttpRequests { requests ->
            requests
                .requestMatchers("/actuator/health", "/actuator/health/**")
                .permitAll()
                .anyRequest()
                .authenticated()
        }

        http.formLogin { formLogin ->
            formLogin.defaultSuccessUrl("/", true)
        }

        http.logout { logout ->
            logout
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
        }

        return http.build()
    }
}
