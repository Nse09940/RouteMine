package com.routemine.routing.application

import com.routemine.common.error.ConflictException
import com.routemine.common.error.NotFoundException
import com.routemine.deliveries.application.DeliveryRoutePolicy
import com.routemine.deliveries.domain.Delivery
import com.routemine.deliveries.domain.DeliveryStatus
import com.routemine.routing.domain.RoutePlanStatus
import com.routemine.routing.domain.StopType
import org.springframework.stereotype.Component

@Component
class DeliveryRoutePolicyAdapter(private val routes: RoutePlanRepository) : DeliveryRoutePolicy {
    override fun validateTransition(delivery: Delivery, target: DeliveryStatus, assigned: List<Delivery>) {
        val routeId = delivery.routePlanId ?: return
        val route = routes.findById(routeId)
            ?: throw NotFoundException("ROUTE_NOT_FOUND", "Delivery route was not found")
        if (route.status != RoutePlanStatus.IN_PROGRESS) {
            throw ConflictException("ROUTE_NOT_STARTED", "Delivery route must be in progress")
        }
        if (target == DeliveryStatus.IN_TRANSIT) return

        val next = route.stops.sortedBy { it.position }.firstOrNull { stop ->
            val status = assigned.firstOrNull { it.id == stop.deliveryId }?.status
            when (stop.type) {
                StopType.PICKUP -> status == null || status == DeliveryStatus.CREATED || status == DeliveryStatus.ASSIGNED
                StopType.DROPOFF -> status !in terminalStatuses
            }
        }
        val expectedType = when (target) {
            DeliveryStatus.PICKED_UP, DeliveryStatus.PICKUP_FAILED -> StopType.PICKUP
            else -> StopType.DROPOFF
        }
        if (next?.deliveryId != delivery.id || next.type != expectedType) {
            throw ConflictException("OUT_OF_ORDER_STOP", "Complete the next route stop first")
        }
    }

    private val terminalStatuses = setOf(
        DeliveryStatus.DELIVERED, DeliveryStatus.CANCELLED,
        DeliveryStatus.PICKUP_FAILED, DeliveryStatus.DELIVERY_FAILED,
    )
}
