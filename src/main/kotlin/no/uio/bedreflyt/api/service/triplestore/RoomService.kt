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

    fun createRoom(bedCategory: Long, roomDescription: String): Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :room$bedCategory a :Room ;
                    :bedCategory $bedCategory ;
                    :roomDescription "$roomDescription" .
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

    fun getAllRooms() : List<Room>? {
        val rooms = mutableListOf<Room>()

        val query =
            """
               SELECT DISTINCT ?bedCategory ?roomDescription WHERE {
                ?obj a prog:Room ;
                    prog:Room_bedCategory ?bedCategory ;
                    prog:Room_roomDescription ?roomDescription .
            }"""

        val resultRooms: ResultSet = repl.interpreter!!.query(query)!!
        if (!resultRooms.hasNext()) {
            return null
        }

        while (resultRooms.hasNext()) {
            val solution: QuerySolution = resultRooms.next()
            val roomId = solution.get("?bedCategory").asLiteral().toString().split("^^")[0].toLong()
            val roomDescription = solution.get("?roomDescription").asLiteral().toString()
            rooms.add(Room(roomId, roomDescription))
        }

        return rooms
    }

    fun updateRoom(oldBedCategory: Long, oldRoomDescription: String, newBedCategory: Long, newRoomDescription: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :room$oldBedCategory a :Room ;
                    :bedCategory $oldBedCategory ;
                    :roomDescription "$oldRoomDescription" .
            }
            INSERT {
                :room$newBedCategory a :Room ;
                    :bedCategory $newBedCategory ;
                    :roomDescription "$newRoomDescription" .
            }
            WHERE {
                :room$oldBedCategory a :Room ;
                    :bedCategory $oldBedCategory ;
                    :roomDescription "$oldRoomDescription" .
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

    fun deleteRoom(bedCategory: Long, roomDescription: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :room$bedCategory a :Room ;
                    :bedCategory $bedCategory ;
                    :roomDescription "$roomDescription" .
            }
            WHERE {
                :room$bedCategory a :Room ;
                    :bedCategory $bedCategory ;
                    :roomDescription "$roomDescription" .
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