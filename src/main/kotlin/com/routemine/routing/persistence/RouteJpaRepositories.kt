package com.routemine.routing.persistence

import com.routemine.routing.domain.RoutePlanStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SpringDataRoutePlanRepository : JpaRepository<RoutePlanEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoutePlanEntity r where r.id = :id")
    fun lockById(@Param("id") id: UUID): RoutePlanEntity?

    fun findFirstByCourierIdAndStatusIn(
        courierId: UUID,
        statuses: Set<RoutePlanStatus>,
    ): RoutePlanEntity?
}

interface SpringDataRouteStopRepository : JpaRepository<RouteStopEntity, UUID> {
    fun findAllByRoutePlanIdOrderByPosition(routePlanId: UUID): List<RouteStopEntity>
}

