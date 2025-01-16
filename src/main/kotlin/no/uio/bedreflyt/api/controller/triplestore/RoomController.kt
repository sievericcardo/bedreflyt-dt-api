package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.service.triplestore.RoomService
import no.uio.bedreflyt.api.service.triplestore.TriplestoreService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
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
    private val replConfig: REPLConfig,
    private val triplestoreService: TriplestoreService,
    private val roomService: RoomService
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
        log.info("Adding room")

        if (!roomService.createRoom(roomRequest.bedCategory, roomRequest.roomDescription)) {
            return ResponseEntity.badRequest().body("Error: the room could not be added.")
        }
        replConfig.regenerateSingleModel().invoke("rooms")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = """
            $fileContent
            
            ###  $ttlPrefix/room${roomRequest.bedCategory}
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
        log.info("Getting rooms")
        val rooms = roomService.getAllRooms() ?: return ResponseEntity.badRequest().body(listOf("No rooms found"))
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
    @PatchMapping("/update")
    fun updateRoom(@SwaggerRequestBody(description = "Request to update a room") @RequestBody updateRoomRequest: UpdateRoomRequest) : ResponseEntity<String> {
        log.info("Updating room")

        if(!roomService.updateRoom(updateRoomRequest.oldBedCategory, updateRoomRequest.oldRoomDescription, updateRoomRequest.newBedCategory, updateRoomRequest.newRoomDescription)) {
            return ResponseEntity.badRequest().body("Error: the room could not be updated.")
        }
        replConfig.regenerateSingleModel().invoke("rooms")

        val oldContent = """
            ###  $ttlPrefix/room${updateRoomRequest.oldBedCategory}
            :room${updateRoomRequest.oldBedCategory} rdf:type owl:NamedIndividual ,
                            :Room ;
                :bedCategory ${updateRoomRequest.oldBedCategory} ;
                :roomDescription "${updateRoomRequest.oldRoomDescription}" .
            """.trimIndent()
        val newContent = """
            ###  $ttlPrefix/room${updateRoomRequest.newBedCategory}
            :room${updateRoomRequest.newBedCategory} rdf:type owl:NamedIndividual ,
                            :Room ;
                :bedCategory ${updateRoomRequest.newBedCategory} ;
                :roomDescription "${updateRoomRequest.newRoomDescription}" .
        """.trimIndent()

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, newContent)

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
    @DeleteMapping("/delete")
    fun deleteRoom(@SwaggerRequestBody(description = "Request to delete a room") @RequestBody roomRequest: RoomRequest) : ResponseEntity<String> {
        log.info("Deleting room")

        if(!roomService.deleteRoom(roomRequest.bedCategory, roomRequest.roomDescription)) {
            return ResponseEntity.badRequest().body("Error: the room could not be deleted.")
        }
        replConfig.regenerateSingleModel().invoke("rooms")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/room${roomRequest.bedCategory}
            :room${roomRequest.bedCategory} rdf:type owl:NamedIndividual ,
                            :Room ;
                :bedCategory ${roomRequest.bedCategory} ;
                :roomDescription "${roomRequest.roomDescription}" .
        """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, "")

        return ResponseEntity.ok("Room deleted")
    }
}