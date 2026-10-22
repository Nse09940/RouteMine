package com.routemine.routing.application

import com.routemine.common.domain.GeoPoint
import com.routemine.common.error.BusinessRuleException
import com.routemine.common.error.ConflictException
import com.routemine.common.error.ForbiddenException
import com.routemine.common.error.NotFoundException
import com.routemine.couriers.application.CourierQuery
import com.routemine.deliveries.application.DeliveryOperations
import com.routemine.routing.domain.RouteOptimizer
import com.routemine.routing.domain.RoutePlan
import com.routemine.routing.domain.RoutePlanStatus
import com.routemine.routing.domain.RouteStop
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class RoutePlanningService(
    private val routePlans: RoutePlanRepository,
    private val couriers: CourierQuery,
    private val deliveries: DeliveryOperations,
    private val optimizer: RouteOptimizer,
    private val clock: Clock,
) : RouteActivityQuery {
    @Transactional
    fun plan(courierId: UUID, deliveryIds: Set<UUID>, start: GeoPoint): RoutePlan {
        val courier = couriers.requireAvailable(courierId)
        if (hasActiveRoute(courierId)) {
            throw ConflictException("COURIER_HAS_ACTIVE_ROUTE", "Courier already has an active route")
        }
        deliveries.requireNoUnfinishedAssignments(courierId)
        val planningDeliveries = deliveries.getForPlanning(deliveryIds)
        val solution = optimizer.optimize(start, planningDeliveries, courier.maxWeightGrams)
        val id = UUID.randomUUID()
        val stops = solution.stops.mapIndexed { index, stop ->
            RouteStop(
                id = UUID.randomUUID(),
                routePlanId = id,
                deliveryId = stop.deliveryId,
                type = stop.type,
                position = index + 1,
                location = stop.location,
                distanceFromPreviousMeters = stop.distanceFromPreviousMeters,
            )
        }
        return routePlans.save(
            RoutePlan(
                id = id,
                courierId = courierId,
                status = RoutePlanStatus.DRAFT,
                start = start,
                totalDistanceMeters = solution.totalDistanceMeters,
                version = 0,
                createdAt = clock.instant(),
                confirmedAt = null,
                startedAt = null,
                completedAt = null,
                stops = stops,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): RoutePlan = find(id)

    @Transactional
    fun confirm(id: UUID, actorUserId: UUID): RoutePlan {
        val plan = routePlans.lockById(id)
            ?: throw NotFoundException("ROUTE_NOT_FOUND", "Route plan $id was not found")
        couriers.lockCourier(plan.courierId)
        couriers.requireAvailable(plan.courierId)
        if (routePlans.findActiveByCourierId(plan.courierId)?.id != null) {
            throw ConflictException("COURIER_HAS_ACTIVE_ROUTE", "Courier already has an active route")
        }
        deliveries.requireNoUnfinishedAssignments(plan.courierId)
        deliveries.assignAll(plan.stops.map { it.deliveryId }.toSet(), plan.courierId, plan.id, actorUserId)
        return routePlans.save(plan.confirm(clock.instant()))
    }

    @Transactional
    fun start(id: UUID, courierId: UUID): RoutePlan {
        val plan = lockOwned(id, courierId)
        couriers.lockCourier(courierId)
        return routePlans.save(plan.start(clock.instant()))
    }

    @Transactional
    fun complete(id: UUID, courierId: UUID): RoutePlan {
        val plan = lockOwned(id, courierId)
        couriers.lockCourier(courierId)
        if (!deliveries.allTerminal(plan.stops.map { it.deliveryId }.toSet())) {
            throw BusinessRuleException("ROUTE_NOT_FINISHED", "All route deliveries must be finished first")
        }
        return routePlans.save(plan.complete(clock.instant()))
    }

    @Transactional(readOnly = true)
    fun active(courierId: UUID): RoutePlan = routePlans.findActiveByCourierId(courierId)
        ?: throw NotFoundException("ACTIVE_ROUTE_NOT_FOUND", "Courier does not have an active route")

    @Transactional(readOnly = true)
    override fun hasActiveRoute(courierId: UUID): Boolean = routePlans.findActiveByCourierId(courierId) != null

    private fun lockOwned(id: UUID, courierId: UUID): RoutePlan {
        val plan = routePlans.lockById(id)
            ?: throw NotFoundException("ROUTE_NOT_FOUND", "Route $id was not found")
        if (plan.courierId != courierId) {
            throw ForbiddenException("ROUTE_NOT_ASSIGNED", "Route is not assigned to the current courier")
        }
        return plan
    }

    private fun find(id: UUID): RoutePlan = routePlans.findById(id)
        ?: throw NotFoundException("ROUTE_NOT_FOUND", "Route $id was not found")
}

