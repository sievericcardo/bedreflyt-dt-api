package no.uio.bedreflyt.api.repository

import no.uio.bedreflyt.api.model.RoomDistribution
import org.springframework.data.jpa.repository.JpaRepository

interface RoomDistributionRepository : JpaRepository<RoomDistribution?, String?> {
    fun findByRoomDescription(roomDescription: String?): RoomDistribution? {
        return null
    }
}