package com.routemine.deliveries.application

import com.routemine.common.domain.GeoPoint
import com.routemine.common.error.ConflictException
import com.routemine.common.error.ForbiddenException
import com.routemine.common.error.NotFoundException
import com.routemine.common.error.BusinessRuleException
import com.routemine.couriers.application.CourierQuery
import com.routemine.deliveries.domain.Delivery
import com.routemine.deliveries.domain.DeliveryEvent
import com.routemine.deliveries.domain.DeliveryStatus
import com.routemine.deliveries.domain.PlanningDelivery
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID

data class NewDelivery(
    val description: String,
    val weightGrams: Long,
    val recipientName: String,
    val recipientPhone: String,
    val confirmationCode: String,
    val pickup: GeoPoint,
    val dropoff: GeoPoint,
)

@Service
class DeliveryService(
    private val deliveries: DeliveryRepository,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock,
    private val couriers: CourierQuery,
    private val routePolicy: DeliveryRoutePolicy,
) : DeliveryOperations {
    @Transactional
    fun create(command: NewDelivery, actorUserId: UUID): Delivery {
        val now = clock.instant()
        val delivery = deliveries.save(
            Delivery(
                id = UUID.randomUUID(),
                description = command.description.trim(),
                weightGrams = command.weightGrams,
                recipientName = command.recipientName.trim(),
                recipientPhone = command.recipientPhone.trim(),
                confirmationCodeHash = requireNotNull(passwordEncoder.encode(command.confirmationCode)),
                pickup = command.pickup,
                dropoff = command.dropoff,
                status = DeliveryStatus.CREATED,
                assignedCourierId = null,
                routePlanId = null,
                version = 0,
                createdAt = now,
                updatedAt = now,
            ),
        )
        deliveries.saveEvent(
            DeliveryEvent(
                deliveryId = delivery.id,
                fromStatus = null,
                toStatus = DeliveryStatus.CREATED,
                actorUserId = actorUserId,
                occurredAt = now,
            ),
        )
        return delivery
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): Delivery = find(id)

    @Transactional(readOnly = true)
    fun list(status: DeliveryStatus?): List<Delivery> = deliveries.findAll(status)

    @Transactional(readOnly = true)
    fun listForCourier(courierId: UUID): List<Delivery> = deliveries.findAllByCourierId(courierId)

    @Transactional(readOnly = true)
    fun history(id: UUID): List<DeliveryEvent> {
        find(id)
        return deliveries.findEvents(id)
    }

    @Transactional
    fun cancel(id: UUID, actorUserId: UUID): Delivery = transition(id, DeliveryStatus.CANCELLED, actorUserId)

    @Transactional(noRollbackFor = [ConfirmationCodeException::class])
    fun courierTransition(id: UUID, target: DeliveryStatus, courierId: UUID, actorUserId: UUID, code: String? = null): Delivery {
        // Serialize load changes, assignments and route lifecycle operations for this courier.
        val courier = couriers.lockCourier(courierId)
        val current = deliveries.lockAllByIds(setOf(id)).singleOrNull()
            ?: throw NotFoundException("DELIVERY_NOT_FOUND", "Delivery $id was not found")
        if (current.assignedCourierId != courierId) {
            throw ForbiddenException("DELIVERY_NOT_ASSIGNED", "Delivery is not assigned to the current courier")
        }
        current.transitionTo(target, clock.instant())
        val assigned = deliveries.findAllByCourierId(courierId)
        routePolicy.validateTransition(current, target, assigned)
        if (target == DeliveryStatus.PICKED_UP) {
            var remaining = courier.maxWeightGrams
            for (delivery in assigned.filter { it.status == DeliveryStatus.PICKED_UP || it.status == DeliveryStatus.IN_TRANSIT } + current) {
                if (delivery.weightGrams > remaining) {
                    throw BusinessRuleException("CAPACITY_EXCEEDED", "Pickup exceeds courier capacity")
                }
                remaining -= delivery.weightGrams
            }
        }
        if (target == DeliveryStatus.DELIVERED) {
            verifyConfirmationCode(current, code)
        }
        val verified = if (target == DeliveryStatus.DELIVERED) {
            current.copy(failedConfirmationAttempts = 0, confirmationLockedUntil = null)
        } else current
        return transition(verified, target, actorUserId)
    }

    private fun verifyConfirmationCode(current: Delivery, code: String?) {
        val now = clock.instant()
        val lockedUntil = current.confirmationLockedUntil
        if (lockedUntil != null && now.isBefore(lockedUntil)) throw ConfirmationCodeException(blocked = true)
        if (code != null && passwordEncoder.matches(code, current.confirmationCodeHash)) return

        val attempts = (if (lockedUntil != null) 0 else current.failedConfirmationAttempts) + 1
        val blocked = attempts >= 5
        deliveries.save(current.copy(
            failedConfirmationAttempts = attempts,
            confirmationLockedUntil = if (blocked) now.plus(Duration.ofMinutes(15)) else null,
        ))
        // This exception must commit the failed attempt, including when returning HTTP 429.
        throw ConfirmationCodeException(blocked)
    }

    @Transactional(readOnly = true)
    override fun requireNoUnfinishedAssignments(courierId: UUID) {
        if (deliveries.findAllByCourierId(courierId).any {
            it.status in setOf(DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT)
        }) {
            throw ConflictException("COURIER_HAS_UNFINISHED_ASSIGNMENTS", "Courier has unfinished deliveries")
        }
    }

    @Transactional(readOnly = true)
    override fun getForPlanning(ids: Set<UUID>): List<PlanningDelivery> {
        require(ids.isNotEmpty()) { "At least one delivery is required" }
        val found = deliveries.findAllByIds(ids)
        if (found.size != ids.size) {
            throw NotFoundException("DELIVERY_NOT_FOUND", "One or more deliveries were not found")
        }
        found.forEach {
            if (it.status != DeliveryStatus.CREATED || it.assignedCourierId != null) {
                throw ConflictException("DELIVERY_NOT_AVAILABLE", "Delivery ${it.id} is not available for planning")
            }
        }
        return found.map { PlanningDelivery(it.id, it.weightGrams, it.pickup, it.dropoff) }
    }

    override fun assignAll(
        ids: Set<UUID>,
        courierId: UUID,
        routePlanId: UUID?,
        actorUserId: UUID,
    ): List<UUID> {
        val locked = deliveries.lockAllByIds(ids)
        if (locked.size != ids.size) {
            throw NotFoundException("DELIVERY_NOT_FOUND", "One or more deliveries were not found")
        }
        val now = clock.instant()
        val assigned = locked.map { delivery ->
            if (delivery.status != DeliveryStatus.CREATED || delivery.assignedCourierId != null) {
                throw ConflictException("DELIVERY_NOT_AVAILABLE", "Delivery ${delivery.id} was already changed")
            }
            delivery.assign(courierId, routePlanId, now)
        }
        deliveries.saveAll(assigned)
        assigned.forEach {
            deliveries.saveEvent(
                DeliveryEvent(
                    deliveryId = it.id,
                    fromStatus = DeliveryStatus.CREATED,
                    toStatus = DeliveryStatus.ASSIGNED,
                    actorUserId = actorUserId,
                    occurredAt = now,
                ),
            )
        }
        return assigned.map { it.id }
    }

    @Transactional(readOnly = true)
    override fun allTerminal(ids: Set<UUID>): Boolean {
        val found = deliveries.findAllByIds(ids)
        if (found.size != ids.size) {
            throw NotFoundException("DELIVERY_NOT_FOUND", "One or more deliveries were not found")
        }
        val terminal = setOf(
            DeliveryStatus.DELIVERED,
            DeliveryStatus.CANCELLED,
            DeliveryStatus.PICKUP_FAILED,
            DeliveryStatus.DELIVERY_FAILED,
        )
        return found.all { it.status in terminal }
    }

    private fun transition(id: UUID, target: DeliveryStatus, actorUserId: UUID): Delivery =
        transition(find(id), target, actorUserId)

    private fun transition(current: Delivery, target: DeliveryStatus, actorUserId: UUID): Delivery {
        val updated = current.transitionTo(target, clock.instant())
        val saved = deliveries.save(updated)
        deliveries.saveEvent(
            DeliveryEvent(
                deliveryId = saved.id,
                fromStatus = current.status,
                toStatus = saved.status,
                actorUserId = actorUserId,
                occurredAt = saved.updatedAt,
            ),
        )
        return saved
    }

    private fun find(id: UUID): Delivery = deliveries.findById(id)
        ?: throw NotFoundException("DELIVERY_NOT_FOUND", "Delivery $id was not found")
}
