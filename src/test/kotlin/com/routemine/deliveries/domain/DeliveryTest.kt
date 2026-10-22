package com.routemine.deliveries.domain

import com.routemine.common.domain.GeoPoint
import com.routemine.common.error.BusinessRuleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.time.Instant
import java.util.UUID

class DeliveryTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `delivery follows the successful lifecycle`() {
        val courierId = UUID.randomUUID()
        val created = delivery()

        val delivered = created
            .assign(courierId, null, now)
            .transitionTo(DeliveryStatus.PICKED_UP, now)
            .transitionTo(DeliveryStatus.IN_TRANSIT, now)
            .transitionTo(DeliveryStatus.DELIVERED, now)

        assertEquals(DeliveryStatus.DELIVERED, delivered.status)
        assertEquals(courierId, delivered.assignedCourierId)
    }

    @Test
    fun `delivery cannot skip pickup`() {
        assertFailsWith<BusinessRuleException> {
            delivery().transitionTo(DeliveryStatus.DELIVERED, now)
        }
    }

    private fun delivery() = Delivery(
        id = UUID.randomUUID(),
        description = "Box",
        weightGrams = 1_000,
        recipientName = "Recipient",
        recipientPhone = "+70000000000",
        confirmationCodeHash = "hash",
        pickup = GeoPoint(55.75, 37.61),
        dropoff = GeoPoint(55.76, 37.62),
        status = DeliveryStatus.CREATED,
        assignedCourierId = null,
        routePlanId = null,
        version = 0,
        createdAt = now,
        updatedAt = now,
    )
}

