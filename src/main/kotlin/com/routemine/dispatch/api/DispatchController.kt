package com.routemine.dispatch.api

import com.routemine.dispatch.application.DispatchService
import com.routemine.identity.security.CurrentActor
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class ManualAssignmentRequest(
    val deliveryId: UUID,
    val courierId: UUID,
)

@RestController
@RequestMapping("/api/v1/assignments")
class DispatchController(
    private val service: DispatchService,
    private val currentActor: CurrentActor,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DISPATCHER')")
    fun assign(@RequestBody request: ManualAssignmentRequest) {
        service.assign(request.deliveryId, request.courierId, currentActor.userId())
    }
}

