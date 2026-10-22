package com.routemine.routing.persistence

import com.routemine.routing.domain.RoutePlanStatus
import com.routemine.routing.domain.StopType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "route_plans")
class RoutePlanEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "courier_id", nullable = false)
    var courierId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RoutePlanStatus = RoutePlanStatus.DRAFT,
    @Column(name = "start_latitude", nullable = false)
    var startLatitude: Double = 0.0,
    @Column(name = "start_longitude", nullable = false)
    var startLongitude: Double = 0.0,
    @Column(name = "total_distance_meters", nullable = false)
    var totalDistanceMeters: Long = 0,
    @Version
    @Column(nullable = false)
    var version: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,
    @Column(name = "started_at")
    var startedAt: Instant? = null,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
)

@Entity
@Table(name = "route_stops")
class RouteStopEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "route_plan_id", nullable = false)
    var routePlanId: UUID = UUID.randomUUID(),
    @Column(name = "delivery_id", nullable = false)
    var deliveryId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", nullable = false, length = 20)
    var stopType: StopType = StopType.PICKUP,
    @Column(nullable = false)
    var position: Int = 1,
    @Column(nullable = false)
    var latitude: Double = 0.0,
    @Column(nullable = false)
    var longitude: Double = 0.0,
    @Column(name = "distance_from_previous_meters", nullable = false)
    var distanceFromPreviousMeters: Long = 0,
)

