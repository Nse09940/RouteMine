package com.routemine.deliveries.application

import com.routemine.deliveries.domain.Delivery
import com.routemine.deliveries.domain.DeliveryStatus

interface DeliveryRoutePolicy {
    fun validateTransition(delivery: Delivery, target: DeliveryStatus, assigned: List<Delivery>)
}
