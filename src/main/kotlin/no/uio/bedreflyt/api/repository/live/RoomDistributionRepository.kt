package no.uio.bedreflyt.api.repository.live

import no.uio.bedreflyt.api.model.live.RoomDistribution
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RoomDistributionRepository : JpaRepository<RoomDistribution?, String?> {
    fun findByRoomNumber (roomNumber: Long): RoomDistribution
    fun findByRoom_Id(roomId: Long): List<RoomDistribution>
}