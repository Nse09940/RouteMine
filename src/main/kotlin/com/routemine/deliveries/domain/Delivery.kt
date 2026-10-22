package com.routemine.deliveries.domain

import com.routemine.common.domain.GeoPoint
import com.routemine.common.error.BusinessRuleException
import java.time.Instant
import java.util.UUID

enum class DeliveryStatus {
    CREATED,
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED,
    PICKUP_FAILED,
    DELIVERY_FAILED,
}

data class Delivery(
    val id: UUID,
    val description: String,
    val weightGrams: Long,
    val recipientName: String,
    val recipientPhone: String,
    val confirmationCodeHash: String,
    val pickup: GeoPoint,
    val dropoff: GeoPoint,
    val status: DeliveryStatus,
    val assignedCourierId: UUID?,
    val routePlanId: UUID?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val failedConfirmationAttempts: Int = 0,
    val confirmationLockedUntil: Instant? = null,
) {
    fun assign(courierId: UUID, routeId: UUID?, at: Instant): Delivery {
        requireTransition(DeliveryStatus.ASSIGNED)
        return copy(
            status = DeliveryStatus.ASSIGNED,
            assignedCourierId = courierId,
            routePlanId = routeId,
            updatedAt = at,
        )
    }

    fun transitionTo(target: DeliveryStatus, at: Instant): Delivery {
        requireTransition(target)
        return copy(status = target, updatedAt = at)
    }

    private fun requireTransition(target: DeliveryStatus) {
        val allowed = when (status) {
            DeliveryStatus.CREATED -> setOf(DeliveryStatus.ASSIGNED, DeliveryStatus.CANCELLED)
            DeliveryStatus.ASSIGNED -> setOf(
                DeliveryStatus.PICKED_UP,
                DeliveryStatus.PICKUP_FAILED,
                DeliveryStatus.CANCELLED,
            )
            DeliveryStatus.PICKED_UP -> setOf(DeliveryStatus.IN_TRANSIT)
            DeliveryStatus.IN_TRANSIT -> setOf(DeliveryStatus.DELIVERED, DeliveryStatus.DELIVERY_FAILED)
            DeliveryStatus.DELIVERED,
            DeliveryStatus.CANCELLED,
            DeliveryStatus.PICKUP_FAILED,
            DeliveryStatus.DELIVERY_FAILED,
            -> emptySet()
        }
        if (target !in allowed) {
            throw BusinessRuleException(
                "INVALID_DELIVERY_TRANSITION",
                "Delivery cannot move from $status to $target",
            )
        }
    }
}

data class DeliveryEvent(
    val id: Long? = null,
    val deliveryId: UUID,
    val fromStatus: DeliveryStatus?,
    val toStatus: DeliveryStatus,
    val actorUserId: UUID?,
    val occurredAt: Instant,
)

data class PlanningDelivery(
    val id: UUID,
    val weightGrams: Long,
    val pickup: GeoPoint,
    val dropoff: GeoPoint,
)

