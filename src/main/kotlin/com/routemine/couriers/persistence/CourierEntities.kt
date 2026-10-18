package com.routemine.couriers.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "couriers")
class CourierEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 120)
    var name: String = "",

    @Column(name = "max_weight_grams", nullable = false)
    var maxWeightGrams: Long = 1,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
)

@Entity
@Table(name = "shifts")
class ShiftEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "courier_id", nullable = false)
    var courierId: UUID = UUID.randomUUID(),

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.EPOCH,

    @Column(name = "ended_at")
    var endedAt: Instant? = null,
)

