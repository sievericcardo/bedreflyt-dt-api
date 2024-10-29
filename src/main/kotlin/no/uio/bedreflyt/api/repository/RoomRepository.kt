package no.uio.bedreflyt.api.repository

import no.uio.bedreflyt.api.model.Room
import org.springframework.data.jpa.repository.JpaRepository

interface RoomRepository : JpaRepository<Room?, String?> {
    fun findByRoomDescription(roomDescription: String): Room
}