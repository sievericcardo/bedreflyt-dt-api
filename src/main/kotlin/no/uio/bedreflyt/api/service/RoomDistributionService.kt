package no.uio.bedreflyt.api.service

import no.uio.bedreflyt.api.model.RoomDistribution
import no.uio.bedreflyt.api.repository.RoomDistributionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RoomDistributionService @Autowired constructor(
    private val roomDistributionRepository: RoomDistributionRepository
) {
    fun getRoomDistribution(roomDescription: String): RoomDistribution {
        return roomDistributionRepository.findByRoomDescription(roomDescription) ?: throw IllegalArgumentException("Room distribution not found")
    }
}