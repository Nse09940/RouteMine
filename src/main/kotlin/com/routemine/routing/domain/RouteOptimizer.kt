package com.routemine.routing.domain

import com.routemine.common.domain.GeoPoint
import com.routemine.common.error.BusinessRuleException
import com.routemine.deliveries.domain.PlanningDelivery
import org.springframework.stereotype.Component

interface DistanceMatrixProvider {
    fun calculate(points: List<GeoPoint>): Array<LongArray>
}

data class OptimizedStop(
    val deliveryId: java.util.UUID,
    val type: StopType,
    val location: GeoPoint,
    val distanceFromPreviousMeters: Long,
)

data class RouteSolution(
    val stops: List<OptimizedStop>,
    val totalDistanceMeters: Long,
)

interface RouteOptimizer {
    fun optimize(start: GeoPoint, deliveries: List<PlanningDelivery>, capacityGrams: Long): RouteSolution
}

@Component
class ExactRouteOptimizer(
    private val distanceMatrixProvider: DistanceMatrixProvider,
) : RouteOptimizer {
    override fun optimize(
        start: GeoPoint,
        deliveries: List<PlanningDelivery>,
        capacityGrams: Long,
    ): RouteSolution {
        require(deliveries.isNotEmpty()) { "At least one delivery is required" }
        if (deliveries.size > MAX_DELIVERIES) {
            throw BusinessRuleException(
                "TOO_MANY_DELIVERIES",
                "A route can contain at most $MAX_DELIVERIES deliveries",
            )
        }
        deliveries.firstOrNull { it.weightGrams > capacityGrams }?.let {
            throw BusinessRuleException(
                "CAPACITY_EXCEEDED",
                "Delivery ${it.id} exceeds courier capacity",
            )
        }

        val points = buildList {
            add(start)
            addAll(deliveries.map { it.pickup })
            addAll(deliveries.map { it.dropoff })
        }
        val matrix = distanceMatrixProvider.calculate(points)
        val fullMask = (1 shl deliveries.size) - 1
        val memo = HashMap<State, Result>()

        fun solve(state: State): Result {
            if (state.deliveredMask == fullMask) return Result(0, emptyList())
            memo[state]?.let { return it }

            val load = deliveries.indices.sumOf { index ->
                val picked = state.pickedMask and (1 shl index) != 0
                val delivered = state.deliveredMask and (1 shl index) != 0
                if (picked && !delivered) deliveries[index].weightGrams else 0L
            }

            var best: Result? = null
            for (index in deliveries.indices) {
                val bit = 1 shl index
                if (state.pickedMask and bit == 0 && load + deliveries[index].weightGrams <= capacityGrams) {
                    val node = 1 + index
                    val tail = solve(State(state.pickedMask or bit, state.deliveredMask, node))
                    best = choose(best, Result(matrix[state.node][node] + tail.cost, listOf(node) + tail.nodes))
                }
                if (state.pickedMask and bit != 0 && state.deliveredMask and bit == 0) {
                    val node = 1 + deliveries.size + index
                    val tail = solve(State(state.pickedMask, state.deliveredMask or bit, node))
                    best = choose(best, Result(matrix[state.node][node] + tail.cost, listOf(node) + tail.nodes))
                }
            }
            return (best ?: throw BusinessRuleException("ROUTE_NOT_FOUND", "No feasible route was found"))
                .also { memo[state] = it }
        }

        val result = solve(State(0, 0, 0))
        var previous = 0
        val stops = result.nodes.map { node ->
            val pickup = node <= deliveries.size
            val deliveryIndex = if (pickup) node - 1 else node - 1 - deliveries.size
            val delivery = deliveries[deliveryIndex]
            OptimizedStop(
                deliveryId = delivery.id,
                type = if (pickup) StopType.PICKUP else StopType.DROPOFF,
                location = points[node],
                distanceFromPreviousMeters = matrix[previous][node],
            ).also { previous = node }
        }
        return RouteSolution(stops, result.cost)
    }

    private fun choose(current: Result?, candidate: Result): Result =
        if (current == null || candidate.cost < current.cost) candidate else current

    private data class State(val pickedMask: Int, val deliveredMask: Int, val node: Int)
    private data class Result(val cost: Long, val nodes: List<Int>)

    companion object {
        const val MAX_DELIVERIES = 8
    }
}

