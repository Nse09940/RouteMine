package com.routemine.couriers.application

import com.routemine.couriers.domain.Courier
import com.routemine.couriers.domain.Shift
import java.util.UUID

interface CourierRepository {
    fun save(courier: Courier): Courier
    fun findById(id: UUID): Courier?
    fun lockById(id: UUID): Courier?
    fun findAll(): List<Courier>
    fun findOpenShift(courierId: UUID): Shift?
    fun saveShift(shift: Shift): Shift
}

interface CourierQuery {
    fun lockCourier(id: UUID): Courier
    fun getCourier(id: UUID): Courier
    fun requireAvailable(id: UUID): Courier
}

