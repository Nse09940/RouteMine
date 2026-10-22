package com.routemine.routing.application

import com.routemine.routing.domain.RoutePlan
import java.util.UUID

interface RoutePlanRepository {
    fun save(routePlan: RoutePlan): RoutePlan
    fun findById(id: UUID): RoutePlan?
    fun lockById(id: UUID): RoutePlan?
    fun findActiveByCourierId(courierId: UUID): RoutePlan?
}

interface RouteActivityQuery {
    fun hasActiveRoute(courierId: UUID): Boolean
}

