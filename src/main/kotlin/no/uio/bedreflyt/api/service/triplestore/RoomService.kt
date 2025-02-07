package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Room
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.apache.jena.update.UpdateRequest
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class RoomService (
    private val replConfig: REPLConfig,
    private val triplestoreProperties: TriplestoreProperties
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    @CachePut("rooms", key = "#roomNumber")
    fun createRoom(roomNumber: Int, roomNumberModel: Int, room: Long, capacity: Int, bathroom: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :room$roomNumber a :Room ;
                    :roomNumber $roomNumber ;
                    :roomNumberModel $roomNumberModel ;
                    :roomCategory $room ;
                    :capacity $capacity ;
                    :bathroom $bathroom .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    @Cacheable("rooms")
    fun getAllRooms() : List<Room>? {
        val rooms: MutableList<Room> = mutableListOf()

        val query =  """
            SELECT DISTINCT ?roomNumber ?roomNumberModel ?roomCategory ?capacity ?bathroom WHERE {
                ?obj a prog:Room ;
                    prog:Room_roomNumber ?roomNumber ;
                    prog:Room_roomNumberModel ?roomNumberModel ;
                    prog:Room_roomCategory ?roomCategory ;
                    prog:Room_capacity ?capacity ;
                    prog:Room_bathroom ?bathroom .
            }"""

        val resultRooms: ResultSet = repl.interpreter!!.query(query)!!
        if (!resultRooms.hasNext()) {
            return null
        }

        while (resultRooms.hasNext()) {
            val solution: QuerySolution = resultRooms.next()
            val roomNumber = solution.get("?roomNumber").asLiteral().toString().split("^^")[0].toInt()
            val roomNumberModel = solution.get("?roomNumberModel").asLiteral().toString().split("^^")[0].toInt()
            val room = solution.get("?roomCategory").asLiteral().toString().split("^^")[0].toLong()
            val capacity = solution.get("?capacity").asLiteral().toString().split("^^")[0].toInt()
            val bathroom = solution.get("?bathroom").asLiteral().toString().split("^^")[0].toInt()
            val bathBool = bathroom == 1
            rooms.add(Room(roomNumber, roomNumberModel, room, capacity, bathBool))
        }

        return rooms
    }

    @Cacheable("rooms", key = "#roomNumber")
    fun getRoomByRoomNumber(roomNumber: Int): Room? {
        val query = """
            SELECT DISTINCT ?roomNumber ?roomNumberModel ?roomCategory ?capacity ?bathroom WHERE {
                ?obj a prog:Room ;
                    prog:Room_roomNumber $roomNumber ;
                    prog:Room_roomNumberModel ?roomNumberModel ;
                    prog:Room_roomCategory ?roomCategory ;
                    prog:Room_capacity ?capacity ;
                    prog:Room_bathroom ?bathroom .
            }"""

        val resultRooms: ResultSet = repl.interpreter!!.query(query)!!
        if (!resultRooms.hasNext()) {
            return null
        }

        val solution: QuerySolution = resultRooms.next()
        val roomNumberModel = solution.get("?roomNumberModel").asLiteral().toString().split("^^")[0].toInt()
        val room = solution.get("?roomCategory").asLiteral().toString().split("^^")[0].toLong()
        val capacity = solution.get("?capacity").asLiteral().toString().split("^^")[0].toInt()
        val bathroom = solution.get("?bathroom").asLiteral().toString().split("^^")[0].toInt()
        val bathBool = bathroom == 1
        return Room(roomNumber, roomNumberModel, room, capacity, bathBool)
    }

    @CacheEvict(value = ["rooms"])
    @CachePut(value = ["rooms"])
    fun updateRoom(room: Room, newRoomNumberModel: Int, newRoom: Long, newCapacity: Int, newBathroom: Int) : Boolean {
        val bathroom = if (room.bathroom) 1 else 0
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :room${room.roomNumber} a :Room ;
                    :roomNumber ${room.roomNumber} ;
                    :roomNumberModel ${room.roomNumberModel} ;
                    :roomCategory ${room.roomCategory} ;
                    :capacity ${room.capacity} ;
                    :bathroom $bathroom .
            }
            INSERT {
                :room${room.roomNumber} a :Room ;
                    :roomNumber ${room.roomNumber} ;
                    :roomNumberModel $newRoomNumberModel ;
                    :roomCategory $newRoom ;
                    :capacity $newCapacity ;
                    :bathroom $newBathroom .
            }
            WHERE {
                :room${room.roomNumber} a :Room ;
                    :roomNumber ${room.roomNumber} ;
                    :roomNumberModel ${room.roomNumberModel} ;
                    :roomCategory ${room.roomCategory} ;
                    :capacity ${room.capacity} ;
                    :bathroom $bathroom .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    @CacheEvict(value = ["rooms"], key = "#roomNumber")
    fun deleteRoom(roomNumber: Int) : Boolean {
        val room = getRoomByRoomNumber(roomNumber) ?: return true
        val bathroom = if (room.bathroom) 1 else 0

        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :room$roomNumber a :Room ;
                    :roomNumber $roomNumber ;
                    :roomNumberModel ${room.roomNumberModel} ;
                    :roomCategory ${room.roomCategory} ;
                    :capacity ${room.capacity} ;
                    :bathroom $bathroom .
            }
            WHERE {
                :room$roomNumber a :Room ;
                    :roomNumber $roomNumber ;
                    :roomNumberModel ${room.roomNumberModel} ;
                    :roomCategory ${room.roomCategory} ;
                    :capacity ${room.capacity} ;
                    :bathroom ${bathroom} .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }
}