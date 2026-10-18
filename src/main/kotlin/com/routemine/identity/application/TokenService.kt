package com.routemine.identity.application

import com.routemine.identity.config.SecurityProperties
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.stereotype.Service
import java.time.Clock

data class IssuedToken(val value: String, val expiresInSeconds: Long)

@Service
class TokenService(
    private val jwtEncoder: JwtEncoder,
    private val properties: SecurityProperties,
    private val clock: Clock,
) {
    fun issue(user: AuthenticatedUser): IssuedToken {
        val issuedAt = clock.instant()
        val expiresAt = issuedAt.plus(properties.tokenTtl)
        val claims = JwtClaimsSet.builder()
            .subject(user.id.toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("username", user.username)
            .claim("roles", listOf(user.role.name))
            .apply { user.courierId?.let { claim("courierId", it.toString()) } }
            .build()
        return IssuedToken(
            jwtEncoder.encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(),
                    claims,
                ),
            ).tokenValue,
            properties.tokenTtl.seconds,
        )
    }
}
