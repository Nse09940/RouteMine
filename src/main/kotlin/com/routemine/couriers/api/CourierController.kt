package com.routemine.couriers.api

import com.routemine.couriers.application.CourierService
import com.routemine.couriers.domain.CourierAvailability
import com.routemine.couriers.domain.Shift
import com.routemine.identity.security.CurrentActor
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class CreateCourierRequest(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:Positive val maxWeightGrams: Long,
    @field:NotBlank @field:Pattern(regexp = "[a-zA-Z0-9._-]{3,80}") val username: String,
    @field:Size(min = 8, max = 72) val password: String,
)

data class CourierResponse(
    val id: UUID,
    val name: String,
    val maxWeightGrams: Long,
    val shiftOpen: Boolean,
    val createdAt: Instant,
)

data class ShiftResponse(
    val id: UUID,
    val courierId: UUID,
    val startedAt: Instant,
    val endedAt: Instant?,
)

@RestController
@RequestMapping("/api/v1/couriers")
class CourierController(
    private val service: CourierService,
    private val currentActor: CurrentActor,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DISPATCHER')")
    fun create(@Valid @RequestBody request: CreateCourierRequest): CourierResponse =
        service.create(request.name, request.maxWeightGrams, request.username, request.password).toResponse()

    @GetMapping
    @PreAuthorize("hasRole('DISPATCHER')")
    fun list(): List<CourierResponse> = service.list().map { it.toResponse() }

    @PostMapping("/me/shifts/open")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COURIER')")
    fun openShift(): ShiftResponse = service.openShift(currentActor.courierId()).toResponse()

    @PostMapping("/me/shifts/close")
    @PreAuthorize("hasRole('COURIER')")
    fun closeShift(): ShiftResponse = service.closeShift(currentActor.courierId()).toResponse()

    private fun CourierAvailability.toResponse() = CourierResponse(
        courier.id,
        courier.name,
        courier.maxWeightGrams,
        shiftOpen,
        courier.createdAt,
    )

    private fun Shift.toResponse() = ShiftResponse(id, courierId, startedAt, endedAt)
}

