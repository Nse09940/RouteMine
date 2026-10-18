package com.routemine.identity.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("routemine.security")
data class SecurityProperties(
    val jwtSecret: String,
    val tokenTtl: Duration,
)

@ConfigurationProperties("routemine.bootstrap")
data class BootstrapProperties(
    val dispatcherUsername: String,
    val dispatcherPassword: String,
)

