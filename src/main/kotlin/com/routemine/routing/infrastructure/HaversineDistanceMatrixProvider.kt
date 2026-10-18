package com.routemine.routing.infrastructure

import com.routemine.common.domain.GeoPoint
import com.routemine.routing.domain.DistanceMatrixProvider
import org.springframework.stereotype.Component
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

@Component
class HaversineDistanceMatrixProvider : DistanceMatrixProvider {
    override fun calculate(points: List<GeoPoint>): Array<LongArray> =
        Array(points.size) { from ->
            LongArray(points.size) { to -> distance(points[from], points[to]) }
        }

    private fun distance(from: GeoPoint, to: GeoPoint): Long {
        if (from == to) return 0
        val latitudeDelta = Math.toRadians(to.latitude - from.latitude)
        val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
        val fromLatitude = Math.toRadians(from.latitude)
        val toLatitude = Math.toRadians(to.latitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(fromLatitude) * cos(toLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return (EARTH_RADIUS_METERS * 2 * asin(sqrt(a))).roundToLong()
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

