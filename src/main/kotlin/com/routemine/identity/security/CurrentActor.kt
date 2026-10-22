package com.routemine.identity.security

import com.routemine.common.error.ForbiddenException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CurrentActor {
    fun userId(): UUID = UUID.fromString(authentication().token.subject)

    fun courierId(): UUID {
        val claim = authentication().token.getClaimAsString("courierId")
            ?: throw ForbiddenException("COURIER_ACCOUNT_REQUIRED", "The current account is not linked to a courier")
        return UUID.fromString(claim)
    }

    private fun authentication(): JwtAuthenticationToken =
        SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
            ?: throw ForbiddenException("AUTHENTICATION_REQUIRED", "Authentication is required")
}

