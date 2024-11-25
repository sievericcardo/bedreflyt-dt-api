package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Room
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.apache.jena.update.UpdateRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.util.logging.Logger

data class RoomRequest (
    val bedCategory: Long,
    val roomDescription: String
)

data class UpdateRoomRequest (
    val oldBedCategory: Long,
    val oldRoomDescription: String,
    val newBedCategory: Long,
    val newRoomDescription: String
)

@RestController
@RequestMapping("/api/fuseki/room")
class RoomController (
    private val replConfig: REPLConfig
) {

    private val log : Logger = Logger.getLogger(RoomController::class.java.name)
    private val host = System.getenv().getOrDefault("TRIPLESTORE_URL", "localhost")
    private val dataStore = System.getenv().getOrDefault("TRIPLESTORE_DATASET", "Bedreflyt")
    private val tripleStore = "http://$host:3030/$dataStore"
    private val prefix = System.getenv().getOrDefault("DOMAIN_PREFIX", "http://www.smolang.org/bedreflyt#")
    private val ttlPrefix = if (prefix.isNotEmpty()) prefix.dropLast(1) else prefix
    private val repl = replConfig.repl()

    @Operation(summary = "Add a room")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Journey step added"),
        ApiResponse(responseCode = "400", description = "Invalid journey step"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/create")
    fun addRoom(@SwaggerRequestBody(description = "Journey step to add") @RequestBody roomRequest: RoomRequest) : ResponseEntity<String> {
        log.info("Adding journey step")

        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :room${roomRequest.bedCategory} a :Room ;
                    :bedCategory ${roomRequest.bedCategory} ;
                    :roomDescription "${roomRequest.roomDescription}" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body("Error: the update query could not be executed.")
        }

        repl.interpreter!!.tripleManager.regenerateTripleStoreModel()
        repl.interpreter!!.evalCall(
            repl.interpreter!!.getObjectNames("AssetModel")[0],
            "AssetModel",
            "reconfigure")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = """
            $fileContent
            
            ###  http://$ttlPrefix/room${roomRequest.bedCategory}
            :room${roomRequest.bedCategory} rdf:type owl:NamedIndividual ,
                            :Room ;
                :bedCategory ${roomRequest.bedCategory} ;
                :roomDescription "${roomRequest.roomDescription}" .
        """.trimIndent()

        File(path).writeText(newContent)

        return ResponseEntity.ok("Journey step added")
    }

    @Operation(summary = "Get all rooms")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Rooms found"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/retrieve")
    fun getRooms() : ResponseEntity<List<Any>> {
        val query = """
            SELECT ?bedCategory ?roomDescription
            WHERE {
                ?obj a prog:Room ;
                    prog:Room_bedCategory ?bedCategory ;
                    prog:Room_roomDescription ?roomDescription .
            }
        """.trimIndent()

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!

        val rooms = mutableListOf<Room>()

        if (!resultSet.hasNext()) {
            return ResponseEntity.badRequest().body(listOf("No diagnosis found"))
        }
        while (resultSet.hasNext()) {
            val qs: QuerySolution = resultSet.next()
            val bedCategory = qs.get("bedCategory").asLiteral().toString().split("^^")[0].toLong()
            val roomDescription = qs.get("roomDescription").asLiteral().toString()
            rooms.add(Room(bedCategory, roomDescription))
        }

        return ResponseEntity.ok(rooms)
    }

    @Operation(summary = "Update a room")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room updated"),
        ApiResponse(responseCode = "400", description = "Invalid room"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/update")
    fun updateRoom(@SwaggerRequestBody(description = "Request to update a room") @RequestBody updateRoomRequest: UpdateRoomRequest) : ResponseEntity<String> {
        log.info("Updating room")

        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :room${updateRoomRequest.oldBedCategory} a :Room ;
                    :bedCategory ${updateRoomRequest.oldBedCategory} ;
                    :roomDescription "${updateRoomRequest.oldRoomDescription}" .
            }
            INSERT {
                :room${updateRoomRequest.newBedCategory} a :Room ;
                    :bedCategory ${updateRoomRequest.newBedCategory} ;
                    :roomDescription "${updateRoomRequest.newRoomDescription}" .
            }
            WHERE {
                :room${updateRoomRequest.oldBedCategory} a :Room ;
                    :bedCategory ${updateRoomRequest.oldBedCategory} ;
                    :roomDescription "${updateRoomRequest.oldRoomDescription}" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body("Error: the update query could not be executed.")
        }

        repl.interpreter!!.tripleManager.regenerateTripleStoreModel()
        repl.interpreter!!.evalCall(
            repl.interpreter!!.getObjectNames("AssetModel")[0],
            "AssetModel",
            "reconfigure")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = fileContent.replace("""
            ###  http://$ttlPrefix/room${updateRoomRequest.oldBedCategory}
            :room${updateRoomRequest.oldBedCategory} rdf:type owl:NamedIndividual ,
                            :Room ;
                :bedCategory ${updateRoomRequest.oldBedCategory} ;
                :roomDescription "${updateRoomRequest.oldRoomDescription}" .
        """.trimIndent(), """
            ###  http://$ttlPrefix/room${updateRoomRequest.newBedCategory}
            :room${updateRoomRequest.newBedCategory} rdf:type owl:NamedIndividual ,
                            :Room ;
                :bedCategory ${updateRoomRequest.newBedCategory} ;
                :roomDescription "${updateRoomRequest.newRoomDescription}" .
        """.trimIndent())

        File(path).writeText(newContent)

        return ResponseEntity.ok("Room updated")
    }

    @Operation(summary = "Delete a room")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room deleted"),
        ApiResponse(responseCode = "400", description = "Invalid room"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/delete")
    fun deleteRoom(@SwaggerRequestBody(description = "Request to delete a room") @RequestBody roomRequest: RoomRequest) : ResponseEntity<String> {
        log.info("Deleting room")

        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :room${roomRequest.bedCategory} a :Room ;
                    :bedCategory ${roomRequest.bedCategory} ;
                    :roomDescription "${roomRequest.roomDescription}" .
            }
            WHERE {
                :room${roomRequest.bedCategory} a :Room ;
                    :bedCategory ${roomRequest.bedCategory} ;
                    :roomDescription "${roomRequest.roomDescription}" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body("Error: the update query could not be executed.")
        }

        repl.interpreter!!.tripleManager.regenerateTripleStoreModel()
        repl.interpreter!!.evalCall(
            repl.interpreter!!.getObjectNames("AssetModel")[0],
            "AssetModel",
            "reconfigure")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = fileContent.replace("""
            ###  http://$ttlPrefix/room${roomRequest.bedCategory}
            :room${roomRequest.bedCategory} rdf:type owl:NamedIndividual ,
                            :Room ;
                :bedCategory ${roomRequest.bedCategory} ;
                :roomDescription "${roomRequest.roomDescription}" .
        """.trimIndent(), "")

        File(path).writeText(newContent)

        return ResponseEntity.ok("Room deleted")
    }
}