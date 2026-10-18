package com.routemine.deliveries.persistence

import com.routemine.deliveries.domain.DeliveryStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SpringDataDeliveryRepository : JpaRepository<DeliveryEntity, UUID> {
    fun findAllByStatusOrderByCreatedAtDesc(status: DeliveryStatus): List<DeliveryEntity>
    fun findAllByOrderByCreatedAtDesc(): List<DeliveryEntity>
    fun findAllByAssignedCourierIdOrderByCreatedAtDesc(courierId: UUID): List<DeliveryEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DeliveryEntity d where d.id in :ids order by d.id")
    fun lockAllByIds(@Param("ids") ids: Set<UUID>): List<DeliveryEntity>
}

interface SpringDataDeliveryEventRepository : JpaRepository<DeliveryEventEntity, Long> {
    fun findAllByDeliveryIdOrderByOccurredAtAsc(deliveryId: UUID): List<DeliveryEventEntity>
}
