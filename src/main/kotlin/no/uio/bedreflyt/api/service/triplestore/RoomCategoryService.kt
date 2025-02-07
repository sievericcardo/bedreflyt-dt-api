package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.RoomCategory
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
class RoomCategoryService (
    private val replConfig: REPLConfig,
    private val triplestoreProperties: TriplestoreProperties
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    @CachePut("roomCategories", key = "#bedCategory")
    fun createRoom(bedCategory: Long, roomDescription: String): Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :room$bedCategory a :RoomCategory ;
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

    @Cacheable("roomCategories")
    fun getAllRooms() : List<RoomCategory>? {
        val roomCategories = mutableListOf<RoomCategory>()

        val query =
            """
               SELECT DISTINCT ?bedCategory ?roomDescription WHERE {
                ?obj a prog:RoomCategory ;
                    prog:RoomCategory_bedCategory ?bedCategory ;
                    prog:RoomCategory_roomDescription ?roomDescription .
            }"""

        val resultRooms: ResultSet = repl.interpreter!!.query(query)!!
        if (!resultRooms.hasNext()) {
            return null
        }

        while (resultRooms.hasNext()) {
            val solution: QuerySolution = resultRooms.next()
            val roomId = solution.get("?bedCategory").asLiteral().toString().split("^^")[0].toLong()
            val roomDescription = solution.get("?roomDescription").asLiteral().toString()
            roomCategories.add(RoomCategory(roomId, roomDescription))
        }

        return roomCategories
    }

    @CacheEvict(value = ["roomCategories"], key = "#oldBedCategory")
    @CachePut(value = ["roomCategories"], key = "#newBedCategory")
    fun updateRoom(oldBedCategory: Long, oldRoomDescription: String, newBedCategory: Long, newRoomDescription: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :room$oldBedCategory a :RoomCategory ;
                    :bedCategory $oldBedCategory ;
                    :roomDescription "$oldRoomDescription" .
            }
            INSERT {
                :room$newBedCategory a :RoomCategory ;
                    :bedCategory $newBedCategory ;
                    :roomDescription "$newRoomDescription" .
            }
            WHERE {
                :room$oldBedCategory a :RoomCategory ;
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

    @CacheEvict("roomCategories", key = "#bedCategory")
    fun deleteRoom(bedCategory: Long, roomDescription: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :room$bedCategory a :RoomCategory ;
                    :bedCategory $bedCategory ;
                    :roomDescription "$roomDescription" .
            }
            WHERE {
                :room$bedCategory a :RoomCategory ;
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