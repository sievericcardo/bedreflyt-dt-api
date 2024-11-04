package no.uio.bedreflyt.api.repository.live

import no.uio.bedreflyt.api.model.live.RoomDistribution
import org.springframework.data.jpa.repository.JpaRepository

interface RoomDistributionRepository : JpaRepository<RoomDistribution?, String?> {
    fun findByRoom_RoomDescription(roomDescription: String): List<RoomDistribution>
}