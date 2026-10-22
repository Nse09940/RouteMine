package com.routemine.identity.persistence

import com.routemine.identity.domain.UserRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true, length = 80)
    var username: String = "",

    @Column(name = "password_hash", nullable = false, length = 100)
    var passwordHash: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: UserRole = UserRole.DISPATCHER,

    @Column(name = "courier_id", unique = true)
    var courierId: UUID? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
)

