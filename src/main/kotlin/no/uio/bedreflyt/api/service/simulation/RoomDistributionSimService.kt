package no.uio.bedreflyt.api.service.simulation

import no.uio.bedreflyt.api.model.simulation.RoomDistributionSim
import no.uio.bedreflyt.api.repository.simulation.RoomDistributionSimRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RoomDistributionSimService @Autowired constructor(
    private val roomDistributionSimRepository: RoomDistributionSimRepository
) {
    fun findAll(): MutableList<RoomDistributionSim?> {
        return roomDistributionSimRepository.findAll()
    }

    fun findByRoom_RoomDescription(roomDescription: String, sqliteDbUrl: String? = null): List<RoomDistributionSim> {
        return roomDistributionSimRepository.findByRoom_RoomDescription(roomDescription)
    }

    fun saveRoomDistribution(roomDistributionSim: RoomDistributionSim, sqliteDbUrl: String? = null): RoomDistributionSim {
        return roomDistributionSimRepository.save(roomDistributionSim)
    }
}