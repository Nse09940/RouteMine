package com.routemine.deliveries.application

import com.routemine.deliveries.domain.PlanningDelivery
import java.util.UUID

interface DeliveryOperations {
    fun requireNoUnfinishedAssignments(courierId: UUID)
    fun getForPlanning(ids: Set<UUID>): List<PlanningDelivery>
    fun assignAll(ids: Set<UUID>, courierId: UUID, routePlanId: UUID?, actorUserId: UUID): List<UUID>
    fun allTerminal(ids: Set<UUID>): Boolean
}
