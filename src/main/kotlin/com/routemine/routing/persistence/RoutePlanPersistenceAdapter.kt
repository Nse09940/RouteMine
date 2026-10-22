package com.routemine.routing.persistence

import com.routemine.common.domain.GeoPoint
import com.routemine.routing.application.RoutePlanRepository
import com.routemine.routing.domain.RoutePlan
import com.routemine.routing.domain.RoutePlanStatus
import com.routemine.routing.domain.RouteStop
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class RoutePlanPersistenceAdapter(
    private val routeJpa: SpringDataRoutePlanRepository,
    private val stopJpa: SpringDataRouteStopRepository,
) : RoutePlanRepository {
    override fun save(routePlan: RoutePlan): RoutePlan {
        val saved = routeJpa.save(routePlan.toEntity())
        if (routePlan.stops.isNotEmpty()) {
            stopJpa.saveAll(routePlan.stops.map { it.toEntity() })
        }
        return saved.toDomain(stopJpa.findAllByRoutePlanIdOrderByPosition(saved.id))
    }

    override fun findById(id: UUID): RoutePlan? = routeJpa.findById(id).orElse(null)?.withStops()

    override fun lockById(id: UUID): RoutePlan? = routeJpa.lockById(id)?.withStops()

    override fun findActiveByCourierId(courierId: UUID): RoutePlan? = routeJpa
        .findFirstByCourierIdAndStatusIn(
            courierId,
            setOf(RoutePlanStatus.CONFIRMED, RoutePlanStatus.IN_PROGRESS),
        )?.withStops()

    private fun RoutePlanEntity.withStops() = toDomain(stopJpa.findAllByRoutePlanIdOrderByPosition(id))

    private fun RoutePlan.toEntity() = RoutePlanEntity(
        id,
        courierId,
        status,
        start.latitude,
        start.longitude,
        totalDistanceMeters,
        version,
        createdAt,
        confirmedAt,
        startedAt,
        completedAt,
    )

    private fun RouteStop.toEntity() = RouteStopEntity(
        id,
        routePlanId,
        deliveryId,
        type,
        position,
        location.latitude,
        location.longitude,
        distanceFromPreviousMeters,
    )

    private fun RoutePlanEntity.toDomain(stops: List<RouteStopEntity>) = RoutePlan(
        id,
        courierId,
        status,
        GeoPoint(startLatitude, startLongitude),
        totalDistanceMeters,
        version,
        createdAt,
        confirmedAt,
        startedAt,
        completedAt,
        stops.map { it.toDomain() },
    )

    private fun RouteStopEntity.toDomain() = RouteStop(
        id,
        routePlanId,
        deliveryId,
        stopType,
        position,
        GeoPoint(latitude, longitude),
        distanceFromPreviousMeters,
    )
}

