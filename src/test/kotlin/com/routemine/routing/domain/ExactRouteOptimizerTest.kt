package com.routemine.routing.domain

import com.routemine.common.domain.GeoPoint
import com.routemine.common.error.BusinessRuleException
import com.routemine.deliveries.domain.PlanningDelivery
import com.routemine.routing.infrastructure.HaversineDistanceMatrixProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.util.UUID

class ExactRouteOptimizerTest {
    private val optimizer = ExactRouteOptimizer(HaversineDistanceMatrixProvider())

    @Test
    fun `pickup always precedes its dropoff`() {
        val deliveries = listOf(
            delivery(2_000, 55.751, 37.610, 55.760, 37.620),
            delivery(2_000, 55.752, 37.611, 55.761, 37.621),
        )

        val solution = optimizer.optimize(GeoPoint(55.750, 37.600), deliveries, 10_000)

        assertEquals(4, solution.stops.size)
        deliveries.forEach { delivery ->
            val pickup = solution.stops.indexOfFirst { it.deliveryId == delivery.id && it.type == StopType.PICKUP }
            val dropoff = solution.stops.indexOfFirst { it.deliveryId == delivery.id && it.type == StopType.DROPOFF }
            assertTrue(pickup in 0 until dropoff)
        }
        assertEquals(solution.stops.sumOf { it.distanceFromPreviousMeters }, solution.totalDistanceMeters)
    }

    @Test
    fun `capacity can force a dropoff before the next pickup`() {
        val first = delivery(7_000, 55.751, 37.610, 55.760, 37.620)
        val second = delivery(7_000, 55.752, 37.611, 55.761, 37.621)

        val solution = optimizer.optimize(GeoPoint(55.750, 37.600), listOf(first, second), 10_000)

        var load = 0L
        solution.stops.forEach { stop ->
            val weight = if (stop.deliveryId == first.id) first.weightGrams else second.weightGrams
            load += if (stop.type == StopType.PICKUP) weight else -weight
            assertTrue(load in 0..10_000)
        }
    }

    @Test
    fun `single overweight delivery has no feasible route`() {
        assertFailsWith<BusinessRuleException> {
            optimizer.optimize(
                GeoPoint(55.750, 37.600),
                listOf(delivery(11_000, 55.751, 37.610, 55.760, 37.620)),
                10_000,
            )
        }
    }

    private fun delivery(
        weight: Long,
        pickupLat: Double,
        pickupLon: Double,
        dropoffLat: Double,
        dropoffLon: Double,
    ) = PlanningDelivery(
        UUID.randomUUID(),
        weight,
        GeoPoint(pickupLat, pickupLon),
        GeoPoint(dropoffLat, dropoffLon),
    )
}

