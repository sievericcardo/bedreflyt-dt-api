package no.uio.bedreflyt.api.repository.simulation

import no.uio.bedreflyt.api.model.simulation.RoomSim
import org.springframework.data.jpa.repository.JpaRepository

interface RoomSimRepository : JpaRepository<RoomSim?, String?> {
    fun findByRoomDescription(roomDescription: String): RoomSim
}