package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.RoomDistribution
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.apache.jena.update.UpdateRequest

class RoomDistributionService (
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
                :roomDistribution$roomNumber a :RoomDistribution ;
                    :roomNumber $roomNumber ;
                    :roomNumberModel $roomNumberModel ;
                    :room $room ;
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

    fun getAllRoomDistributions() : List<RoomDistribution>? {
        val roomDistributions: MutableList<RoomDistribution> = mutableListOf()

        val query =  """
            SELECT DISTINCT ?roomNumber ?roomNumberModel ?room ?capacity ?bathroom WHERE {
                ?obj a prog:RoomDistribution ;
                    prog:RoomDistribution_roomNumber ?roomNumber ;
                    prog:RoomDistribution_roomNumberModel ?roomNumberModel ;
                    prog:RoomDistribution_room ?room ;
                    prog:RoomDistribution_capacity ?capacity ;
                    prog:RoomDistribution_bathroom ?bathroom .
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
            roomDistributions.add(RoomDistribution(roomNumber, roomNumberModel, room, capacity, bathBool))
        }

        return roomDistributions
    }

    fun updateRoomDistribution(oldRoomNumber: Int, oldRoomNumberModel: Int, oldRoom: Long, oldCapacity: Int, oldBathroom: Int,
                               newRoomNumber: Int, newRoomNumberModel: Int, newRoom: Long, newCapacity: Int, newBathroom: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :roomDistribution$oldRoomNumber a :RoomDistribution ;
                    :roomNumber $oldRoomNumber ;
                    :roomNumberModel $oldRoomNumberModel ;
                    :room $oldRoom ;
                    :capacity $oldCapacity ;
                    :bathroom $oldBathroom .
            }
            INSERT {
                :roomDistribution$newRoomNumber a :RoomDistribution ;
                    :roomNumber $newRoomNumber ;
                    :roomNumberModel $newRoomNumberModel ;
                    :room $newRoom ;
                    :capacity $newCapacity ;
                    :bathroom $newBathroom .
            }
            WHERE {
                :roomDistribution$oldRoomNumber a :RoomDistribution ;
                    :roomNumber $oldRoomNumber ;
                    :roomNumberModel $oldRoomNumberModel ;
                    :room $oldRoom ;
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
                :roomDistribution$roomNumber a :RoomDistribution ;
                    :roomNumber $roomNumber ;
                    :roomNumberModel $roomNumberModel ;
                    :room $room ;
                    :capacity $capacity ;
                    :bathroom $bathroom .
            }
            WHERE {
                :roomDistribution$roomNumber a :RoomDistribution ;
                    :roomNumber $roomNumber ;
                    :roomNumberModel $roomNumberModel ;
                    :room $room ;
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