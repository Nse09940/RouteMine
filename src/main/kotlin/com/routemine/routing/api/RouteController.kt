package com.routemine.routing.api

import com.routemine.common.domain.GeoPoint
import com.routemine.identity.security.CurrentActor
import com.routemine.routing.application.RoutePlanningService
import com.routemine.routing.domain.RoutePlan
import com.routemine.routing.domain.RoutePlanStatus
import com.routemine.routing.domain.StopType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class CreateRoutePlanRequest(
    val courierId: UUID,
    @field:NotEmpty @field:Size(max = 8) val deliveryIds: Set<UUID>,
    @field:Valid val start: RoutePointRequest,
)

data class RoutePointRequest(
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0") val latitude: Double,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0") val longitude: Double,
)

data class RouteStopResponse(
    val id: UUID,
    val deliveryId: UUID,
    val type: StopType,
    val position: Int,
    val location: RoutePointRequest,
    val distanceFromPreviousMeters: Long,
)

data class RoutePlanResponse(
    val id: UUID,
    val courierId: UUID,
    val status: RoutePlanStatus,
    val start: RoutePointRequest,
    val totalDistanceMeters: Long,
    val version: Long,
    val createdAt: Instant,
    val confirmedAt: Instant?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val stops: List<RouteStopResponse>,
)

@RestController
@RequestMapping("/api/v1/route-plans")
class RoutePlanController(
    private val service: RoutePlanningService,
    private val currentActor: CurrentActor,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DISPATCHER')")
    fun plan(@Valid @RequestBody request: CreateRoutePlanRequest): RoutePlanResponse =
        service.plan(
            request.courierId,
            request.deliveryIds,
            GeoPoint(request.start.latitude, request.start.longitude),
        ).toResponse()

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DISPATCHER')")
    fun get(@PathVariable id: UUID): RoutePlanResponse = service.get(id).toResponse()

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('DISPATCHER')")
    fun confirm(@PathVariable id: UUID): RoutePlanResponse =
        service.confirm(id, currentActor.userId()).toResponse()
}

@RestController
@RequestMapping("/api/v1/courier/routes")
class CourierRouteController(
    private val service: RoutePlanningService,
    private val currentActor: CurrentActor,
) {
    @GetMapping("/active")
    @PreAuthorize("hasRole('COURIER')")
    fun active(): RoutePlanResponse = service.active(currentActor.courierId()).toResponse()

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('COURIER')")
    fun start(@PathVariable id: UUID): RoutePlanResponse = service.start(id, currentActor.courierId()).toResponse()

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('COURIER')")
    fun complete(@PathVariable id: UUID): RoutePlanResponse = service.complete(id, currentActor.courierId()).toResponse()
}

private fun RoutePlan.toResponse() = RoutePlanResponse(
    id,
    courierId,
    status,
    RoutePointRequest(start.latitude, start.longitude),
    totalDistanceMeters,
    version,
    createdAt,
    confirmedAt,
    startedAt,
    completedAt,
    stops.map {
        RouteStopResponse(
            it.id,
            it.deliveryId,
            it.type,
            it.position,
            RoutePointRequest(it.location.latitude, it.location.longitude),
            it.distanceFromPreviousMeters,
        )
    },
)
