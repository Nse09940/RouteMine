package com.routemine

import com.routemine.identity.config.BootstrapProperties
import com.routemine.identity.config.SecurityProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Clock

@SpringBootApplication
@EnableConfigurationProperties(SecurityProperties::class, BootstrapProperties::class)
class RouteMineApplication {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
    runApplication<RouteMineApplication>(*args)
}

