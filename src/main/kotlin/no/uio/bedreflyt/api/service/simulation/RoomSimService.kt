package no.uio.bedreflyt.api.service.simulation

import no.uio.bedreflyt.api.model.simulation.RoomSim
import no.uio.bedreflyt.api.repository.simulation.RoomSimRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RoomSimService @Autowired constructor(
    private val roomSimRepository: RoomSimRepository
) {
    fun findAll() : MutableList<RoomSim?> {
        return roomSimRepository.findAll()
    }

    fun findByRoomDescription(roomDescription: String): RoomSim {
        return roomSimRepository.findByRoomDescription(roomDescription)
    }

    fun saveRoom(room: RoomSim, sqliteDbUrl: String? = null): RoomSim {
        return roomSimRepository.save(room)
    }
}