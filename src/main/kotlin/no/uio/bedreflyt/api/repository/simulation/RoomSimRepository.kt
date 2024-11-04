package no.uio.bedreflyt.api.repository.simulation

import no.uio.bedreflyt.api.model.simulation.Room
import org.springframework.data.jpa.repository.JpaRepository

interface RoomSimRepository : JpaRepository<Room?, String?> {
    fun findByRoomDescription(roomDescription: String): Room
}