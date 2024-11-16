package no.uio.bedreflyt.api.service.live

import no.uio.bedreflyt.api.model.live.Room
import no.uio.bedreflyt.api.repository.live.RoomRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RoomService (
    private val roomRepository: RoomRepository
) {
    fun findAll(): MutableList<Room?> {
        return roomRepository.findAll()
    }

    fun findById(id: Long): Room {
        return roomRepository.findById(id)
    }

    fun findByRoomDescription(roomDescription: String): Room {
        return roomRepository.findByRoomDescription(roomDescription)
    }

    fun saveRoom(room: Room): Room {
        return roomRepository.save(room)
    }

    fun deleteRoom(room: Room) {
        roomRepository.delete(room)
    }
}