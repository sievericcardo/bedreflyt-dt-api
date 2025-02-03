package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.RoomCategory
import no.uio.bedreflyt.api.service.triplestore.RoomCategoryService
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
import no.uio.bedreflyt.api.types.RoomCategoryRequest
import no.uio.bedreflyt.api.types.UpdateRoomCategoryRequest

@RestController
@RequestMapping("/api/fuseki/room-category")
class RoomCategoryController (
    private val replConfig: REPLConfig,
    private val environmentConfig: EnvironmentConfig,
    private val triplestoreService: TriplestoreService,
    private val roomCategoryService: RoomCategoryService
) {

    private val log : Logger = Logger.getLogger(RoomCategoryController::class.java.name)
    private val host = environmentConfig.getOrDefault("TRIPLESTORE_URL", "localhost")
    private val dataStore = environmentConfig.getOrDefault("TRIPLESTORE_DATASET", "Bedreflyt")
    private val tripleStore = "http://$host:3030/$dataStore"
    private val prefix = environmentConfig.getOrDefault("DOMAIN_PREFIX", "http://www.smolang.org/bedreflyt#")
    private val ttlPrefix = if (prefix.isNotEmpty()) prefix.dropLast(1) else prefix
    private val repl = replConfig.repl()

    @Operation(summary = "Add a room")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room category added"),
        ApiResponse(responseCode = "400", description = "Invalid room cateogry"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/create")
    fun addRoomCategory(@SwaggerRequestBody(description = "Room category to add") @RequestBody roomRequest: RoomCategoryRequest) : ResponseEntity<String> {
        log.info("Adding room")

        if (!roomCategoryService.createRoom(roomRequest.bedCategory, roomRequest.roomDescription)) {
            return ResponseEntity.badRequest().body("Error: the room could not be added.")
        }
        replConfig.regenerateSingleModel().invoke("rooms")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = """
            $fileContent
            
            ###  $ttlPrefix/roomCategory${roomRequest.bedCategory}
            :roomCategory${roomRequest.bedCategory} rdf:type owl:NamedIndividual ,
                            :RoomCategory ;
                :bedCategory ${roomRequest.bedCategory} ;
                :roomDescription "${roomRequest.roomDescription}" .
        """.trimIndent()

        File(path).writeText(newContent)

        return ResponseEntity.ok("Room category added")
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
    fun getRoomCategories() : ResponseEntity<List<RoomCategory>> {
        log.info("Getting rooms")
        val rooms = roomCategoryService.getAllRooms() ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(rooms)
    }

    @Operation(summary = "Update a room")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room category updated"),
        ApiResponse(responseCode = "400", description = "Invalid room category"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/update")
    fun updateRoomCategory(@SwaggerRequestBody(description = "Request to update a room") @RequestBody updateRoomRequest: UpdateRoomCategoryRequest) : ResponseEntity<String> {
        log.info("Updating room")

        if(!roomCategoryService.updateRoom(updateRoomRequest.oldBedCategory, updateRoomRequest.oldRoomDescription, updateRoomRequest.newBedCategory, updateRoomRequest.newRoomDescription)) {
            return ResponseEntity.badRequest().body("Error: the room could not be updated.")
        }
        replConfig.regenerateSingleModel().invoke("rooms")

        val oldContent = """
            ###  $ttlPrefix/roomCategory${updateRoomRequest.oldBedCategory}
            :roomCategory${updateRoomRequest.oldBedCategory} rdf:type owl:NamedIndividual ,
                            :RoomCategory ;
                :bedCategory ${updateRoomRequest.oldBedCategory} ;
                :roomDescription "${updateRoomRequest.oldRoomDescription}" .
            """.trimIndent()
        val newContent = """
            ###  $ttlPrefix/roomCategory${updateRoomRequest.newBedCategory}
            :roomCategory${updateRoomRequest.newBedCategory} rdf:type owl:NamedIndividual ,
                            :RoomCategory ;
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
        ApiResponse(responseCode = "200", description = "Room category deleted"),
        ApiResponse(responseCode = "400", description = "Invalid room category"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/delete")
    fun deleteRoomCategory(@SwaggerRequestBody(description = "Request to delete a room") @RequestBody roomRequest: RoomCategoryRequest) : ResponseEntity<String> {
        log.info("Deleting room")

        if(!roomCategoryService.deleteRoom(roomRequest.bedCategory, roomRequest.roomDescription)) {
            return ResponseEntity.badRequest().body("Error: the room could not be deleted.")
        }
        replConfig.regenerateSingleModel().invoke("rooms")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/roomCategory${roomRequest.bedCategory}
            :roomCategory${roomRequest.bedCategory} rdf:type owl:NamedIndividual ,
                            :RoomCategory ;
                :bedCategory ${roomRequest.bedCategory} ;
                :roomDescription "${roomRequest.roomDescription}" .
        """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, "")

        return ResponseEntity.ok("Room deleted")
    }
}