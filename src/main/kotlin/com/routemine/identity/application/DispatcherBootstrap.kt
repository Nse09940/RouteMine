package com.routemine.identity.application

import com.routemine.identity.config.BootstrapProperties
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class DispatcherBootstrap(
    private val authService: AuthService,
    private val properties: BootstrapProperties,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        authService.ensureDispatcher(properties.dispatcherUsername, properties.dispatcherPassword)
    }
}

