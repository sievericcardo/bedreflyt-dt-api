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

    fun createRoomDistribution(roomNumber: Int, roomNumberModel: Int, room: Long, capacity: Int, bathroom: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :roomDistribution$roomNumber a :Room ;
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

    fun getAllRoomDistributions() : List<Room>? {
        val rooms: MutableList<Room> = mutableListOf()

        val query =  """
            SELECT DISTINCT ?roomNumber ?roomNumberModel ?room ?capacity ?bathroom WHERE {
                ?obj a prog:Room ;
                    prog:Room_roomNumber ?roomNumber ;
                    prog:Room_roomNumberModel ?roomNumberModel ;
                    prog:Room_roomCategory ?room ;
                    prog:Room_capacity ?capacity ;
                    prog:Room_bathroom ?bathroom .
            }"""

        val resultRoomDistributions: ResultSet = repl.interpreter!!.query(query)!!
        if (!resultRoomDistributions.hasNext()) {
            return null
        }

        while (resultRoomDistributions.hasNext()) {
            val solution: QuerySolution = resultRoomDistributions.next()
            val roomNumber = solution.get("?roomNumber").asLiteral().toString().split("^^")[0].toInt()
            val roomNumberModel = solution.get("?roomNumberModel").asLiteral().toString().split("^^")[0].toInt()
            val room = solution.get("?room").asLiteral().toString().split("^^")[0].toLong()
            val capacity = solution.get("?capacity").asLiteral().toString().split("^^")[0].toInt()
            val bathroom = solution.get("?bathroom").asLiteral().toString().split("^^")[0].toInt()
            val bathBool = bathroom == 1
            rooms.add(Room(roomNumber, roomNumberModel, room, capacity, bathBool))
        }

        return rooms
    }

    fun updateRoomDistribution(oldRoomNumber: Int, oldRoomNumberModel: Int, oldRoom: Long, oldCapacity: Int, oldBathroom: Int,
                               newRoomNumber: Int, newRoomNumberModel: Int, newRoom: Long, newCapacity: Int, newBathroom: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :roomDistribution$oldRoomNumber a :Room ;
                    :roomNumber $oldRoomNumber ;
                    :roomNumberModel $oldRoomNumberModel ;
                    :roomCategory $oldRoom ;
                    :capacity $oldCapacity ;
                    :bathroom $oldBathroom .
            }
            INSERT {
                :roomDistribution$newRoomNumber a :Room ;
                    :roomNumber $newRoomNumber ;
                    :roomNumberModel $newRoomNumberModel ;
                    :roomCategory $newRoom ;
                    :capacity $newCapacity ;
                    :bathroom $newBathroom .
            }
            WHERE {
                :roomDistribution$oldRoomNumber a :Room ;
                    :roomNumber $oldRoomNumber ;
                    :roomNumberModel $oldRoomNumberModel ;
                    :roomCategory $oldRoom ;
                    :capacity $oldCapacity ;
                    :bathroom $oldBathroom .
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

    fun deleteRoomDistribution (roomNumber: Int, roomNumberModel: Int, room: Long, capacity: Int, bathroom: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :roomDistribution$roomNumber a :Room ;
                    :roomNumber $roomNumber ;
                    :roomNumberModel $roomNumberModel ;
                    :roomCategory $room ;
                    :capacity $capacity ;
                    :bathroom $bathroom .
            }
            WHERE {
                :roomDistribution$roomNumber a :Room ;
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
}