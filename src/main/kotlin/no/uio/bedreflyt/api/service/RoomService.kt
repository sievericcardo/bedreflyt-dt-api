package no.uio.bedreflyt.api.service

import no.uio.bedreflyt.api.model.Room
import no.uio.bedreflyt.api.repository.RoomRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RoomService @Autowired constructor(
    private val roomRepository: RoomRepository
) {
    fun findByRoomDescription(roomDescription: String): Room {
        return roomRepository.findByRoomDescription(roomDescription)
    }
}