package com.routemine.identity.application

import com.routemine.common.error.ConflictException
import com.routemine.common.error.UnauthorizedException
import com.routemine.identity.domain.UserRole
import com.routemine.identity.persistence.UserEntity
import com.routemine.identity.persistence.UserJpaRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class AuthService(
    private val users: UserJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val clock: Clock,
) : UserAccountPort {
    @Transactional(readOnly = true)
    fun login(username: String, password: String): IssuedToken {
        val user = users.findByUsername(username)
            ?: throw UnauthorizedException("INVALID_CREDENTIALS", "Invalid username or password")
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw UnauthorizedException("INVALID_CREDENTIALS", "Invalid username or password")
        }
        return tokenService.issue(user.toAuthenticatedUser())
    }

    @Transactional
    override fun createCourierAccount(username: String, rawPassword: String, courierId: UUID): UUID {
        if (users.existsByUsername(username)) {
            throw ConflictException("USERNAME_EXISTS", "Username is already in use")
        }
        return users.save(
            UserEntity(
                username = username,
                passwordHash = requireNotNull(passwordEncoder.encode(rawPassword)),
                role = UserRole.COURIER,
                courierId = courierId,
                createdAt = clock.instant(),
            ),
        ).id
    }

    @Transactional
    fun ensureDispatcher(username: String, rawPassword: String) {
        if (!users.existsByUsername(username)) {
            users.save(
                UserEntity(
                    username = username,
                    passwordHash = requireNotNull(passwordEncoder.encode(rawPassword)),
                    role = UserRole.DISPATCHER,
                    createdAt = clock.instant(),
                ),
            )
        }
    }

    private fun UserEntity.toAuthenticatedUser() = AuthenticatedUser(id, username, role, courierId)
}
