package com.routemine.identity.application

import com.routemine.identity.domain.UserRole
import java.util.UUID

interface UserAccountPort {
    fun createCourierAccount(username: String, rawPassword: String, courierId: UUID): UUID
}

data class AuthenticatedUser(
    val id: UUID,
    val username: String,
    val role: UserRole,
    val courierId: UUID?,
)

