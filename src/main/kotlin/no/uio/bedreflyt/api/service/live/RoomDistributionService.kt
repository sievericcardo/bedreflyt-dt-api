package no.uio.bedreflyt.api.service.live

import no.uio.bedreflyt.api.model.live.RoomDistribution
import no.uio.bedreflyt.api.repository.live.RoomDistributionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RoomDistributionService (
    private val roomDistributionRepository: RoomDistributionRepository
) {
    fun findAll(): MutableList<RoomDistribution?> {
        return roomDistributionRepository.findAll()
    }

    fun findByRoomNumber(roomNumber: Long): RoomDistribution {
        return roomDistributionRepository.findByRoomNumber(roomNumber)
    }

    fun findByRoom_Id(roomId: Long): List<RoomDistribution> {
        return roomDistributionRepository.findByRoom_Id(roomId)
    }

    fun saveRoomDistribution(roomDistribution: RoomDistribution): RoomDistribution {
        return roomDistributionRepository.save(roomDistribution)
    }

    fun deleteRoomDistribution(roomDistribution: RoomDistribution) {
        roomDistributionRepository.delete(roomDistribution)
    }
}