package com.routemine.deliveries.api

import com.routemine.common.domain.GeoPoint
import com.routemine.deliveries.application.DeliveryService
import com.routemine.deliveries.application.NewDelivery
import com.routemine.deliveries.domain.Delivery
import com.routemine.deliveries.domain.DeliveryEvent
import com.routemine.deliveries.domain.DeliveryStatus
import com.routemine.identity.security.CurrentActor
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class PointRequest(
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0") val latitude: Double,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0") val longitude: Double,
)

data class CreateDeliveryRequest(
    @field:NotBlank @field:Size(max = 500) val description: String,
    @field:Positive val weightGrams: Long,
    @field:NotBlank @field:Size(max = 120) val recipientName: String,
    @field:NotBlank @field:Size(max = 40) val recipientPhone: String,
    @field:Pattern(regexp = "[0-9]{4,8}") val confirmationCode: String,
    @field:Valid val pickup: PointRequest,
    @field:Valid val dropoff: PointRequest,
)

data class DeliveryResponse(
    val id: UUID,
    val description: String,
    val weightGrams: Long,
    val recipientName: String,
    val recipientPhone: String,
    val pickup: PointRequest,
    val dropoff: PointRequest,
    val status: DeliveryStatus,
    val assignedCourierId: UUID?,
    val routePlanId: UUID?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class DeliveryEventResponse(
    val id: Long?,
    val fromStatus: DeliveryStatus?,
    val toStatus: DeliveryStatus,
    val actorUserId: UUID?,
    val occurredAt: Instant,
)

data class ConfirmationCodeRequest(
    @field:Pattern(regexp = "[0-9]{4,8}") val confirmationCode: String,
)

@RestController
@RequestMapping("/api/v1/deliveries")
class DeliveryController(
    private val service: DeliveryService,
    private val currentActor: CurrentActor,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DISPATCHER')")
    fun create(@Valid @RequestBody request: CreateDeliveryRequest): DeliveryResponse = service.create(
        NewDelivery(
            request.description,
            request.weightGrams,
            request.recipientName,
            request.recipientPhone,
            request.confirmationCode,
            GeoPoint(request.pickup.latitude, request.pickup.longitude),
            GeoPoint(request.dropoff.latitude, request.dropoff.longitude),
        ),
        currentActor.userId(),
    ).toResponse()

    @GetMapping
    @PreAuthorize("hasRole('DISPATCHER')")
    fun list(@RequestParam(required = false) status: DeliveryStatus?): List<DeliveryResponse> =
        service.list(status).map { it.toResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DISPATCHER')")
    fun get(@PathVariable id: UUID): DeliveryResponse = service.get(id).toResponse()

    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('DISPATCHER')")
    fun history(@PathVariable id: UUID): List<DeliveryEventResponse> =
        service.history(id).map { it.toResponse() }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('DISPATCHER')")
    fun cancel(@PathVariable id: UUID): DeliveryResponse = service.cancel(id, currentActor.userId()).toResponse()

    private fun Delivery.toResponse() = DeliveryResponse(
        id,
        description,
        weightGrams,
        recipientName,
        recipientPhone,
        PointRequest(pickup.latitude, pickup.longitude),
        PointRequest(dropoff.latitude, dropoff.longitude),
        status,
        assignedCourierId,
        routePlanId,
        version,
        createdAt,
        updatedAt,
    )

    private fun DeliveryEvent.toResponse() = DeliveryEventResponse(id, fromStatus, toStatus, actorUserId, occurredAt)
}

@RestController
@RequestMapping("/api/v1/courier/deliveries")
class CourierDeliveryController(
    private val service: DeliveryService,
    private val currentActor: CurrentActor,
) {
    @GetMapping
    @PreAuthorize("hasRole('COURIER')")
    fun listAssigned(): List<DeliveryResponse> =
        service.listForCourier(currentActor.courierId()).map { it.toResponse() }

    @PostMapping("/{id}/pickup")
    @PreAuthorize("hasRole('COURIER')")
    fun pickup(@PathVariable id: UUID): DeliveryResponse = transition(id, DeliveryStatus.PICKED_UP)

    @PostMapping("/{id}/in-transit")
    @PreAuthorize("hasRole('COURIER')")
    fun inTransit(@PathVariable id: UUID): DeliveryResponse = transition(id, DeliveryStatus.IN_TRANSIT)

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasRole('COURIER')")
    fun deliver(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ConfirmationCodeRequest,
    ): DeliveryResponse = transition(id, DeliveryStatus.DELIVERED, request.confirmationCode)

    @PostMapping("/{id}/pickup-failed")
    @PreAuthorize("hasRole('COURIER')")
    fun pickupFailed(@PathVariable id: UUID): DeliveryResponse = transition(id, DeliveryStatus.PICKUP_FAILED)

    @PostMapping("/{id}/delivery-failed")
    @PreAuthorize("hasRole('COURIER')")
    fun deliveryFailed(@PathVariable id: UUID): DeliveryResponse = transition(id, DeliveryStatus.DELIVERY_FAILED)

    private fun transition(id: UUID, target: DeliveryStatus, code: String? = null): DeliveryResponse {
        val result = service.courierTransition(
            id,
            target,
            currentActor.courierId(),
            currentActor.userId(),
            code,
        )
        return result.toResponse()
    }

    private fun Delivery.toResponse() = DeliveryResponse(
        id,
        description,
        weightGrams,
        recipientName,
        recipientPhone,
        PointRequest(pickup.latitude, pickup.longitude),
        PointRequest(dropoff.latitude, dropoff.longitude),
        status,
        assignedCourierId,
        routePlanId,
        version,
        createdAt,
        updatedAt,
    )
}
