package com.routemine.routing.domain

import com.routemine.common.domain.GeoPoint
import com.routemine.common.error.BusinessRuleException
import java.time.Instant
import java.util.UUID

enum class RoutePlanStatus {
    DRAFT,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

enum class StopType {
    PICKUP,
    DROPOFF,
}

data class RouteStop(
    val id: UUID,
    val routePlanId: UUID,
    val deliveryId: UUID,
    val type: StopType,
    val position: Int,
    val location: GeoPoint,
    val distanceFromPreviousMeters: Long,
)

data class RoutePlan(
    val id: UUID,
    val courierId: UUID,
    val status: RoutePlanStatus,
    val start: GeoPoint,
    val totalDistanceMeters: Long,
    val version: Long,
    val createdAt: Instant,
    val confirmedAt: Instant?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val stops: List<RouteStop>,
) {
    fun confirm(at: Instant): RoutePlan {
        requireStatus(RoutePlanStatus.DRAFT)
        return copy(status = RoutePlanStatus.CONFIRMED, confirmedAt = at)
    }

    fun start(at: Instant): RoutePlan {
        requireStatus(RoutePlanStatus.CONFIRMED)
        return copy(status = RoutePlanStatus.IN_PROGRESS, startedAt = at)
    }

    fun complete(at: Instant): RoutePlan {
        requireStatus(RoutePlanStatus.IN_PROGRESS)
        return copy(status = RoutePlanStatus.COMPLETED, completedAt = at)
    }

    private fun requireStatus(expected: RoutePlanStatus) {
        if (status != expected) {
            throw BusinessRuleException(
                "INVALID_ROUTE_TRANSITION",
                "Route must be $expected but is $status",
            )
        }
    }
}

