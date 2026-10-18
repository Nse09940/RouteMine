package com.routemine.couriers.domain

import java.time.Instant
import java.util.UUID

data class Courier(
    val id: UUID,
    val name: String,
    val maxWeightGrams: Long,
    val createdAt: Instant,
)

data class Shift(
    val id: UUID,
    val courierId: UUID,
    val startedAt: Instant,
    val endedAt: Instant? = null,
) {
    val isOpen: Boolean get() = endedAt == null
}

data class CourierAvailability(
    val courier: Courier,
    val shiftOpen: Boolean,
)

