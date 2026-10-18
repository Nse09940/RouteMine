package com.routemine.deliveries.application

import com.routemine.deliveries.domain.Delivery
import com.routemine.deliveries.domain.DeliveryEvent
import com.routemine.deliveries.domain.DeliveryStatus
import java.util.UUID

interface DeliveryRepository {
    fun save(delivery: Delivery): Delivery
    fun saveAll(deliveries: List<Delivery>): List<Delivery>
    fun findById(id: UUID): Delivery?
    fun findAll(status: DeliveryStatus?): List<Delivery>
    fun findAllByCourierId(courierId: UUID): List<Delivery>
    fun findAllByIds(ids: Set<UUID>): List<Delivery>
    fun lockAllByIds(ids: Set<UUID>): List<Delivery>
    fun saveEvent(event: DeliveryEvent): DeliveryEvent
    fun findEvents(deliveryId: UUID): List<DeliveryEvent>
}
