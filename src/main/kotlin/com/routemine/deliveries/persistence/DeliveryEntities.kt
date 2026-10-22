package com.routemine.deliveries.persistence

import com.routemine.deliveries.domain.DeliveryStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "deliveries")
class DeliveryEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 500)
    var description: String = "",
    @Column(name = "weight_grams", nullable = false)
    var weightGrams: Long = 1,
    @Column(name = "recipient_name", nullable = false, length = 120)
    var recipientName: String = "",
    @Column(name = "recipient_phone", nullable = false, length = 40)
    var recipientPhone: String = "",
    @Column(name = "confirmation_code_hash", nullable = false, length = 100)
    var confirmationCodeHash: String = "",
    @Column(name = "pickup_latitude", nullable = false)
    var pickupLatitude: Double = 0.0,
    @Column(name = "pickup_longitude", nullable = false)
    var pickupLongitude: Double = 0.0,
    @Column(name = "dropoff_latitude", nullable = false)
    var dropoffLatitude: Double = 0.0,
    @Column(name = "dropoff_longitude", nullable = false)
    var dropoffLongitude: Double = 0.0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: DeliveryStatus = DeliveryStatus.CREATED,
    @Column(name = "assigned_courier_id")
    var assignedCourierId: UUID? = null,
    @Column(name = "route_plan_id")
    var routePlanId: UUID? = null,
    @Version
    @Column(nullable = false)
    var version: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
    @Column(name = "failed_confirmation_attempts", nullable = false)
    var failedConfirmationAttempts: Int = 0,
    @Column(name = "confirmation_locked_until")
    var confirmationLockedUntil: Instant? = null,
)

@Entity
@Table(name = "delivery_events")
class DeliveryEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "delivery_id", nullable = false)
    var deliveryId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    var fromStatus: DeliveryStatus? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    var toStatus: DeliveryStatus = DeliveryStatus.CREATED,
    @Column(name = "actor_user_id")
    var actorUserId: UUID? = null,
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.EPOCH,
)

