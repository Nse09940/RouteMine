package com.routemine.dispatch.application

import com.routemine.common.error.BusinessRuleException
import com.routemine.common.error.ConflictException
import com.routemine.couriers.application.CourierQuery
import com.routemine.deliveries.application.DeliveryOperations
import com.routemine.routing.application.RouteActivityQuery
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DispatchService(
    private val couriers: CourierQuery,
    private val deliveries: DeliveryOperations,
    private val routes: RouteActivityQuery,
) {
    @Transactional
    fun assign(deliveryId: UUID, courierId: UUID, actorUserId: UUID) {
        couriers.lockCourier(courierId)
        val courier = couriers.requireAvailable(courierId)
        if (routes.hasActiveRoute(courierId)) {
            throw ConflictException("COURIER_HAS_ACTIVE_ROUTE", "Courier already has an active route")
        }
        val delivery = deliveries.getForPlanning(setOf(deliveryId)).single()
        if (delivery.weightGrams > courier.maxWeightGrams) {
            throw BusinessRuleException("CAPACITY_EXCEEDED", "Delivery exceeds courier capacity")
        }
        deliveries.assignAll(setOf(deliveryId), courierId, routePlanId = null, actorUserId)
    }
}

