package com.routemine.couriers.persistence

import org.springframework.data.jpa.repository.JpaRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SpringDataCourierRepository : JpaRepository<CourierEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourierEntity c where c.id = :id")
    fun lockById(@Param("id") id: UUID): CourierEntity?
}

interface SpringDataShiftRepository : JpaRepository<ShiftEntity, UUID> {
    fun findByCourierIdAndEndedAtIsNull(courierId: UUID): ShiftEntity?
}

