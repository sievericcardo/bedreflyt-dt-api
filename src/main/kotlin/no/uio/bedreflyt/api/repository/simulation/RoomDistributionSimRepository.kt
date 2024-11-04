package no.uio.bedreflyt.api.repository.simulation

import no.uio.bedreflyt.api.model.simulation.RoomDistributionSim
import org.springframework.data.jpa.repository.JpaRepository

interface RoomDistributionSimRepository : JpaRepository<RoomDistributionSim?, String?> {
    fun findByRoom_RoomDescription(roomDescription: String): List<RoomDistributionSim>
}