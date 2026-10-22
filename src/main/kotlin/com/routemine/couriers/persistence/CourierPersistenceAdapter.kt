package com.routemine.couriers.persistence

import com.routemine.couriers.application.CourierRepository
import com.routemine.couriers.domain.Courier
import com.routemine.couriers.domain.Shift
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class CourierPersistenceAdapter(
    private val courierJpa: SpringDataCourierRepository,
    private val shiftJpa: SpringDataShiftRepository,
) : CourierRepository {
    override fun save(courier: Courier): Courier = courierJpa.save(courier.toEntity()).toDomain()

    override fun findById(id: UUID): Courier? = courierJpa.findById(id).orElse(null)?.toDomain()

    override fun lockById(id: UUID): Courier? = courierJpa.lockById(id)?.toDomain()

    override fun findAll(): List<Courier> = courierJpa.findAll().map { it.toDomain() }

    override fun findOpenShift(courierId: UUID): Shift? =
        shiftJpa.findByCourierIdAndEndedAtIsNull(courierId)?.toDomain()

    override fun saveShift(shift: Shift): Shift = shiftJpa.save(shift.toEntity()).toDomain()

    private fun Courier.toEntity() = CourierEntity(id, name, maxWeightGrams, createdAt)
    private fun CourierEntity.toDomain() = Courier(id, name, maxWeightGrams, createdAt)
    private fun Shift.toEntity() = ShiftEntity(id, courierId, startedAt, endedAt)
    private fun ShiftEntity.toDomain() = Shift(id, courierId, startedAt, endedAt)
}

