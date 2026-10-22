package com.routemine

import com.jayway.jsonpath.JsonPath
import com.routemine.deliveries.persistence.SpringDataDeliveryRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RouteMineApiIntegrationTest @Autowired constructor(
    private val mvc: MockMvc,
    private val deliveryRepository: SpringDataDeliveryRepository,
) {
    @Test
    fun `complete delivery route lifecycle`() {
        val dispatcherToken = login("dispatcher", "dispatcher-pass")
        val courierUsername = "courier-${System.nanoTime()}"
        val courierBody = mvc.perform(
            post("/api/v1/couriers")
                .bearer(dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{
                        "name":"Alex Courier",
                        "maxWeightGrams":10000,
                        "username":"$courierUsername",
                        "password":"courier-pass"
                    }""".trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val courierId: String = JsonPath.read(courierBody, "$.id")
        val courierToken = login(courierUsername, "courier-pass")
        mvc.perform(post("/api/v1/couriers/me/shifts/open").bearer(courierToken))
            .andExpect(status().isCreated)

        val first = createDelivery(dispatcherToken, "1111", 55.751, 37.610, 55.760, 37.620)
        val second = createDelivery(dispatcherToken, "2222", 55.752, 37.611, 55.761, 37.621)

        val planBody = mvc.perform(
            post("/api/v1/route-plans")
                .bearer(dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{
                      "courierId":"$courierId",
                      "deliveryIds":["${first.id}","${second.id}"],
                      "start":{"latitude":55.750,"longitude":37.600}
                    }""".trimIndent(),
                ),
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.stops.length()").value(4))
            .andReturn().response.contentAsString
        val routeId: String = JsonPath.read(planBody, "$.id")

        mvc.perform(post("/api/v1/route-plans/$routeId/confirm").bearer(dispatcherToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))

        mvc.perform(get("/api/v1/courier/deliveries").bearer(courierToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))

        mvc.perform(post("/api/v1/courier/routes/$routeId/start").bearer(courierToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))

        val codes = listOf(first, second).associate { it.id to it.code }
        val stops: List<Map<String, Any>> = JsonPath.read(planBody, "$.stops")
        stops.forEach { stop ->
            val deliveryId = stop["deliveryId"] as String
            if (stop["type"] == "PICKUP") {
                mvc.perform(post("/api/v1/courier/deliveries/$deliveryId/pickup").bearer(courierToken))
                    .andExpect(status().isOk)
                mvc.perform(post("/api/v1/courier/deliveries/$deliveryId/in-transit").bearer(courierToken))
                    .andExpect(status().isOk)
            } else mvc.perform(
                post("/api/v1/courier/deliveries/$deliveryId/deliver")
                    .bearer(courierToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"confirmationCode\":\"${codes.getValue(deliveryId)}\"}"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("DELIVERED"))
        }

        mvc.perform(post("/api/v1/courier/routes/$routeId/complete").bearer(courierToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        mvc.perform(get("/api/v1/deliveries/${first.id}/history").bearer(dispatcherToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].toStatus").value("CREATED"))
            .andExpect(jsonPath("$[4].toStatus").value("DELIVERED"))
    }

    @Test
    fun `concurrent assignment gives a delivery to only one courier`() {
        val dispatcherToken = login("dispatcher", "dispatcher-pass")
        val firstCourier = createCourier(dispatcherToken, "First Courier")
        val secondCourier = createCourier(dispatcherToken, "Second Courier")
        val delivery = createDelivery(dispatcherToken, "3333", 55.751, 37.610, 55.760, 37.620)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val attempts = listOf(firstCourier.id, secondCourier.id).map { courierId ->
                executor.submit<Int> {
                    start.await()
                    mvc.perform(
                        post("/api/v1/assignments")
                            .bearer(dispatcherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"deliveryId\":\"${delivery.id}\",\"courierId\":\"$courierId\"}"),
                    ).andReturn().response.status
                }
            }
            start.countDown()
            val statuses = attempts.map { it.get(30, TimeUnit.SECONDS) }.sorted()

            assertEquals(listOf(204, 409), statuses)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `role and parameter errors preserve their HTTP status`() {
        val dispatcher = login("dispatcher", "dispatcher-pass")
        val courier = createCourier(dispatcher, "Restricted Courier")
        mvc.perform(get("/api/v1/deliveries").bearer(courier.token))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
        mvc.perform(get("/api/v1/deliveries/not-a-uuid").bearer(dispatcher))
            .andExpect(status().isBadRequest)
        mvc.perform(get("/api/v1/deliveries?status=unknown").bearer(dispatcher))
            .andExpect(status().isBadRequest)
        mvc.perform(get("/api/v1/deliveries")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `route execution requires start and follows the capacity constrained stop order`() {
        val dispatcher = login("dispatcher", "dispatcher-pass")
        val courier = createCourier(dispatcher, "Route Courier")
        val orders = listOf(
            createDelivery(dispatcher, "1111", 55.751, 37.610, 55.760, 37.620, 7000),
            createDelivery(dispatcher, "2222", 55.752, 37.611, 55.761, 37.621, 7000),
        )
        val body = createPlan(dispatcher, courier, orders)
        val routeId: String = JsonPath.read(body, "$.id")
        val stops: List<Map<String, Any>> = JsonPath.read(body, "$.stops")
        val first = orders.single { it.id == stops.first()["deliveryId"] }
        val second = orders.single { it.id != first.id }
        mvc.perform(post("/api/v1/route-plans/$routeId/confirm").bearer(dispatcher)).andExpect(status().isOk)
        transition(courier, first.id, "pickup").andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ROUTE_NOT_STARTED"))
        mvc.perform(post("/api/v1/courier/routes/$routeId/start").bearer(courier.token)).andExpect(status().isOk)
        transition(courier, second.id, "pickup").andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("OUT_OF_ORDER_STOP"))
        transition(courier, first.id, "pickup").andExpect(status().isOk)
        transition(courier, second.id, "pickup").andExpect(status().isConflict)
        transition(courier, first.id, "in-transit").andExpect(status().isOk)
        deliver(courier, first.id, first.code).andExpect(status().isOk)
        transition(courier, second.id, "pickup").andExpect(status().isOk)
        transition(courier, second.id, "in-transit").andExpect(status().isOk)
        deliver(courier, second.id, second.code).andExpect(status().isOk)
        mvc.perform(post("/api/v1/courier/routes/$routeId/complete").bearer(courier.token)).andExpect(status().isOk)
    }

    @Test
    fun `manual assignments block new plans and confirmation of an earlier draft`() {
        val dispatcher = login("dispatcher", "dispatcher-pass")
        val courier = createCourier(dispatcher, "Busy Courier")
        val manual = createDelivery(dispatcher, "1111", 55.751, 37.610, 55.760, 37.620)
        val planned = createDelivery(dispatcher, "2222", 55.752, 37.611, 55.761, 37.621)
        val draft = createPlan(dispatcher, courier, listOf(planned))
        val routeId: String = JsonPath.read(draft, "$.id")
        assign(dispatcher, courier, manual)
        planRequest(dispatcher, courier, listOf(planned)).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("COURIER_HAS_UNFINISHED_ASSIGNMENTS"))
        mvc.perform(post("/api/v1/route-plans/$routeId/confirm").bearer(dispatcher))
            .andExpect(status().isConflict)
        mvc.perform(get("/api/v1/route-plans/$routeId").bearer(dispatcher))
            .andExpect(jsonPath("$.status").value("DRAFT"))
        mvc.perform(get("/api/v1/deliveries/${planned.id}").bearer(dispatcher))
            .andExpect(jsonPath("$.status").value("CREATED"))
    }

    @Test
    fun `concurrent manual pickups cannot exceed capacity and delivering releases capacity`() {
        val dispatcher = login("dispatcher", "dispatcher-pass")
        val courier = createCourier(dispatcher, "Capacity Courier")
        val orders = listOf(
            createDelivery(dispatcher, "1111", 55.751, 37.610, 55.760, 37.620, 7000),
            createDelivery(dispatcher, "2222", 55.752, 37.611, 55.761, 37.621, 7000),
        )
        orders.forEach { assign(dispatcher, courier, it) }
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val outcomes = try {
            val attempts = orders.map { order -> executor.submit<Pair<TestDelivery, Int>> {
                start.await()
                order to transition(courier, order.id, "pickup").andReturn().response.status
            } }
            start.countDown()
            attempts.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        assertEquals(listOf(200, 422), outcomes.map { it.second }.sorted())
        val picked = outcomes.single { it.second == 200 }.first
        val waiting = outcomes.single { it.second == 422 }.first
        transition(courier, waiting.id, "pickup").andExpect(status().`is`(422))
        transition(courier, picked.id, "in-transit").andExpect(status().isOk)
        deliver(courier, picked.id, picked.code).andExpect(status().isOk)
        transition(courier, waiting.id, "pickup").andExpect(status().isOk)
    }

    @Test
    fun `failed confirmation attempts commit and persist the lock until expiry`() {
        val dispatcher = login("dispatcher", "dispatcher-pass")
        val courier = createCourier(dispatcher, "Confirmation Courier")
        val order = createDelivery(dispatcher, "1111", 55.751, 37.610, 55.760, 37.620)
        assign(dispatcher, courier, order)
        transition(courier, order.id, "pickup").andExpect(status().isOk)
        transition(courier, order.id, "in-transit").andExpect(status().isOk)
        repeat(4) { attempt ->
            deliver(courier, order.id, "9999").andExpect(status().isConflict)
            assertEquals(attempt + 1, deliveryRepository.findById(UUID.fromString(order.id)).orElseThrow().failedConfirmationAttempts)
        }
        deliver(courier, order.id, "9999").andExpect(status().isTooManyRequests)
        deliver(courier, order.id, order.code).andExpect(status().isTooManyRequests)
        val locked = deliveryRepository.findById(UUID.fromString(order.id)).orElseThrow()
        assertEquals(5, locked.failedConfirmationAttempts)
        assertNotNull(locked.confirmationLockedUntil)
        locked.confirmationLockedUntil = Instant.now().minusSeconds(1)
        deliveryRepository.saveAndFlush(locked)
        deliver(courier, order.id, "9999").andExpect(status().isConflict)
        val afterExpiry = deliveryRepository.findById(UUID.fromString(order.id)).orElseThrow()
        assertEquals(1, afterExpiry.failedConfirmationAttempts)
        assertNull(afterExpiry.confirmationLockedUntil)
        deliver(courier, order.id, order.code).andExpect(status().isOk)
        assertEquals(0, deliveryRepository.findById(UUID.fromString(order.id)).orElseThrow().failedConfirmationAttempts)
    }

    @Test
    fun `concurrent confirmation and manual assignment cannot both succeed`() {
        val dispatcher = login("dispatcher", "dispatcher-pass")
        val courier = createCourier(dispatcher, "Assignment Race Courier")
        val manual = createDelivery(dispatcher, "1111", 55.751, 37.610, 55.760, 37.620)
        val planned = createDelivery(dispatcher, "2222", 55.752, 37.611, 55.761, 37.621)
        val routeId: String = JsonPath.read(createPlan(dispatcher, courier, listOf(planned)), "$.id")
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val confirmation = executor.submit<Int> {
                start.await()
                mvc.perform(post("/api/v1/route-plans/$routeId/confirm").bearer(dispatcher)).andReturn().response.status
            }
            val assignment = executor.submit<Int> {
                start.await()
                mvc.perform(post("/api/v1/assignments").bearer(dispatcher).contentType(MediaType.APPLICATION_JSON)
                    .content("""{"deliveryId":"${manual.id}","courierId":"${courier.id}"}"""))
                    .andReturn().response.status
            }
            start.countDown()
            val confirmed = confirmation.get(30, TimeUnit.SECONDS)
            val assigned = assignment.get(30, TimeUnit.SECONDS)
            assertEquals(true, (confirmed == 200 && assigned == 409) || (confirmed == 409 && assigned == 204))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `cancelled and failed pickups do not block remaining route stops`() {
        val dispatcher = login("dispatcher", "dispatcher-pass")
        val courier = createCourier(dispatcher, "Failed Stop Courier")
        val orders = (1..3).map {
            createDelivery(dispatcher, "1111", 55.751, 37.610, 55.760, 37.620, 7000)
        }
        val body = createPlan(dispatcher, courier, orders)
        val routeId: String = JsonPath.read(body, "$.id")
        val stops: List<Map<String, Any>> = JsonPath.read(body, "$.stops")
        val pickups = stops.filter { it["type"] == "PICKUP" }.map { it["deliveryId"] as String }
        mvc.perform(post("/api/v1/route-plans/$routeId/confirm").bearer(dispatcher)).andExpect(status().isOk)
        mvc.perform(post("/api/v1/deliveries/${pickups[0]}/cancel").bearer(dispatcher)).andExpect(status().isOk)
        mvc.perform(post("/api/v1/courier/routes/$routeId/start").bearer(courier.token)).andExpect(status().isOk)
        transition(courier, pickups[1], "pickup-failed").andExpect(status().isOk)
        transition(courier, pickups[2], "pickup").andExpect(status().isOk)
        transition(courier, pickups[2], "in-transit").andExpect(status().isOk)
        transition(courier, pickups[2], "delivery-failed").andExpect(status().isOk)
        mvc.perform(post("/api/v1/courier/routes/$routeId/complete").bearer(courier.token)).andExpect(status().isOk)
    }

    private fun assign(dispatcher: String, courier: TestCourier, order: TestDelivery) {
        mvc.perform(post("/api/v1/assignments").bearer(dispatcher).contentType(MediaType.APPLICATION_JSON)
            .content("""{"deliveryId":"${order.id}","courierId":"${courier.id}"}"""))
            .andExpect(status().isNoContent)
    }

    private fun planRequest(dispatcher: String, courier: TestCourier, orders: List<TestDelivery>) = mvc.perform(
        post("/api/v1/route-plans").bearer(dispatcher).contentType(MediaType.APPLICATION_JSON)
            .content("""{"courierId":"${courier.id}","deliveryIds":[${orders.joinToString { "\"${it.id}\"" }}],"start":{"latitude":55.75,"longitude":37.6}}"""),
    )

    private fun createPlan(dispatcher: String, courier: TestCourier, orders: List<TestDelivery>): String =
        planRequest(dispatcher, courier, orders).andExpect(status().isCreated).andReturn().response.contentAsString

    private fun transition(courier: TestCourier, id: String, action: String) =
        mvc.perform(post("/api/v1/courier/deliveries/$id/$action").bearer(courier.token))

    private fun deliver(courier: TestCourier, id: String, code: String) = mvc.perform(
        post("/api/v1/courier/deliveries/$id/deliver").bearer(courier.token).contentType(MediaType.APPLICATION_JSON)
            .content("""{"confirmationCode":"$code"}"""),
    )

    private fun login(username: String, password: String): String {
        val body = mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","password":"$password"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.read(body, "$.accessToken")
    }

    private fun createDelivery(
        token: String,
        code: String,
        pickupLat: Double,
        pickupLon: Double,
        dropoffLat: Double,
        dropoffLon: Double,
        weightGrams: Long = 2000,
    ): TestDelivery {
        val body = mvc.perform(
            post("/api/v1/deliveries")
                .bearer(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{
                      "description":"Parcel",
                      "weightGrams":$weightGrams,
                      "recipientName":"Recipient",
                      "recipientPhone":"+70000000000",
                      "confirmationCode":"$code",
                      "pickup":{"latitude":$pickupLat,"longitude":$pickupLon},
                      "dropoff":{"latitude":$dropoffLat,"longitude":$dropoffLon}
                    }""".trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return TestDelivery(JsonPath.read(body, "$.id"), code)
    }

    private fun createCourier(dispatcherToken: String, name: String): TestCourier {
        val username = "courier-${System.nanoTime()}"
        val body = mvc.perform(
            post("/api/v1/couriers")
                .bearer(dispatcherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{
                      "name":"$name",
                      "maxWeightGrams":10000,
                      "username":"$username",
                      "password":"courier-pass"
                    }""".trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val id: String = JsonPath.read(body, "$.id")
        val token = login(username, "courier-pass")
        mvc.perform(post("/api/v1/couriers/me/shifts/open").bearer(token))
            .andExpect(status().isCreated)
        return TestCourier(id, token)
    }

    private fun org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder.bearer(token: String) =
        header("Authorization", "Bearer $token")

    private data class TestDelivery(val id: String, val code: String)
    private data class TestCourier(val id: String, val token: String)

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
