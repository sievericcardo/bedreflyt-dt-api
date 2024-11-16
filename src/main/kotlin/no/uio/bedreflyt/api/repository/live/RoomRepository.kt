package no.uio.bedreflyt.api.repository.live

import no.uio.bedreflyt.api.model.live.Room
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RoomRepository : JpaRepository<Room?, String?> {
    fun findById(id: Long): Room
    fun findByRoomDescription(roomDescription: String): Room
}