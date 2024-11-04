package no.uio.bedreflyt.api.repository.live

import no.uio.bedreflyt.api.model.live.Room
import org.springframework.data.jpa.repository.JpaRepository

interface RoomRepository : JpaRepository<Room?, String?> {
    fun findByRoomDescription(roomDescription: String): Room
}