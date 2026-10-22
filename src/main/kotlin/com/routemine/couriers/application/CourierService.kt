package com.routemine.couriers.application

import com.routemine.common.error.ConflictException
import com.routemine.common.error.NotFoundException
import com.routemine.couriers.domain.Courier
import com.routemine.couriers.domain.CourierAvailability
import com.routemine.couriers.domain.Shift
import com.routemine.identity.application.UserAccountPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class CourierService(
    private val couriers: CourierRepository,
    private val userAccounts: UserAccountPort,
    private val clock: Clock,
) : CourierQuery {
    @Transactional
    fun create(name: String, maxWeightGrams: Long, username: String, password: String): CourierAvailability {
        val courier = couriers.save(
            Courier(
                id = UUID.randomUUID(),
                name = name.trim(),
                maxWeightGrams = maxWeightGrams,
                createdAt = clock.instant(),
            ),
        )
        userAccounts.createCourierAccount(username.trim(), password, courier.id)
        return CourierAvailability(courier, shiftOpen = false)
    }

    @Transactional(readOnly = true)
    fun list(): List<CourierAvailability> = couriers.findAll().map {
        CourierAvailability(it, couriers.findOpenShift(it.id) != null)
    }

    @Transactional
    fun openShift(courierId: UUID): Shift {
        lockCourier(courierId)
        if (couriers.findOpenShift(courierId) != null) {
            throw ConflictException("SHIFT_ALREADY_OPEN", "Courier already has an open shift")
        }
        return couriers.saveShift(Shift(UUID.randomUUID(), courierId, clock.instant()))
    }

    @Transactional
    fun closeShift(courierId: UUID): Shift {
        lockCourier(courierId)
        val shift = couriers.findOpenShift(courierId)
            ?: throw ConflictException("SHIFT_NOT_OPEN", "Courier does not have an open shift")
        return couriers.saveShift(shift.copy(endedAt = clock.instant()))
    }

    @Transactional(readOnly = true)
    override fun getCourier(id: UUID): Courier = couriers.findById(id)
        ?: throw NotFoundException("COURIER_NOT_FOUND", "Courier $id was not found")

    @Transactional
    override fun lockCourier(id: UUID): Courier = couriers.lockById(id)
        ?: throw NotFoundException("COURIER_NOT_FOUND", "Courier $id was not found")

    @Transactional(readOnly = true)
    override fun requireAvailable(id: UUID): Courier {
        val courier = getCourier(id)
        if (couriers.findOpenShift(id) == null) {
            throw ConflictException("COURIER_NOT_AVAILABLE", "Courier does not have an open shift")
        }
        return courier
    }
}

