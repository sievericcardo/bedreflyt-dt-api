package no.uio.bedreflyt.api.service.live

import no.uio.bedreflyt.api.model.live.RoomDistribution
import no.uio.bedreflyt.api.repository.live.RoomDistributionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RoomDistributionService @Autowired constructor(
    private val roomDistributionRepository: RoomDistributionRepository
) {
    fun findAll(): MutableList<RoomDistribution?> {
        return roomDistributionRepository.findAll()
    }

    fun findByRoomDescription(roomDescription: String): RoomDistribution {
        return roomDistributionRepository.findByRoomDescription(roomDescription)
    }

    fun saveRoomDistribution(roomDistribution: RoomDistribution): RoomDistribution {
        return roomDistributionRepository.save(roomDistribution)
    }
}