package com.routemine.deliveries.persistence

import com.routemine.common.domain.GeoPoint
import com.routemine.deliveries.application.DeliveryRepository
import com.routemine.deliveries.domain.Delivery
import com.routemine.deliveries.domain.DeliveryEvent
import com.routemine.deliveries.domain.DeliveryStatus
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class DeliveryPersistenceAdapter(
    private val deliveryJpa: SpringDataDeliveryRepository,
    private val eventJpa: SpringDataDeliveryEventRepository,
) : DeliveryRepository {
    override fun save(delivery: Delivery): Delivery = deliveryJpa.save(delivery.toEntity()).toDomain()

    override fun saveAll(deliveries: List<Delivery>): List<Delivery> =
        deliveryJpa.saveAll(deliveries.map { it.toEntity() }).map { it.toDomain() }

    override fun findById(id: UUID): Delivery? = deliveryJpa.findById(id).orElse(null)?.toDomain()

    override fun findAll(status: DeliveryStatus?): List<Delivery> =
        (status?.let { deliveryJpa.findAllByStatusOrderByCreatedAtDesc(it) }
            ?: deliveryJpa.findAllByOrderByCreatedAtDesc()).map { it.toDomain() }

    override fun findAllByCourierId(courierId: UUID): List<Delivery> =
        deliveryJpa.findAllByAssignedCourierIdOrderByCreatedAtDesc(courierId).map { it.toDomain() }

    override fun findAllByIds(ids: Set<UUID>): List<Delivery> = deliveryJpa.findAllById(ids).map { it.toDomain() }

    override fun lockAllByIds(ids: Set<UUID>): List<Delivery> = deliveryJpa.lockAllByIds(ids).map { it.toDomain() }

    override fun saveEvent(event: DeliveryEvent): DeliveryEvent = eventJpa.save(event.toEntity()).toDomain()

    override fun findEvents(deliveryId: UUID): List<DeliveryEvent> =
        eventJpa.findAllByDeliveryIdOrderByOccurredAtAsc(deliveryId).map { it.toDomain() }

    private fun Delivery.toEntity() = DeliveryEntity(
        id,
        description,
        weightGrams,
        recipientName,
        recipientPhone,
        confirmationCodeHash,
        pickup.latitude,
        pickup.longitude,
        dropoff.latitude,
        dropoff.longitude,
        status,
        assignedCourierId,
        routePlanId,
        version,
        createdAt,
        updatedAt,
        failedConfirmationAttempts,
        confirmationLockedUntil,
    )

    private fun DeliveryEntity.toDomain() = Delivery(
        id,
        description,
        weightGrams,
        recipientName,
        recipientPhone,
        confirmationCodeHash,
        GeoPoint(pickupLatitude, pickupLongitude),
        GeoPoint(dropoffLatitude, dropoffLongitude),
        status,
        assignedCourierId,
        routePlanId,
        version,
        createdAt,
        updatedAt,
        failedConfirmationAttempts,
        confirmationLockedUntil,
    )

    private fun DeliveryEvent.toEntity() = DeliveryEventEntity(
        id,
        deliveryId,
        fromStatus,
        toStatus,
        actorUserId,
        occurredAt,
    )

    private fun DeliveryEventEntity.toDomain() = DeliveryEvent(
        id,
        deliveryId,
        fromStatus,
        toStatus,
        actorUserId,
        occurredAt,
    )
}
