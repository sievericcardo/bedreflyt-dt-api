package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.EnvironmentConfig
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
import no.uio.bedreflyt.api.types.RoomRequest
import no.uio.bedreflyt.api.types.UpdateRoomRequest

@RestController
@RequestMapping("/api/fuseki/room")
class RoomDistributionController (
    private val replConfig: REPLConfig,
    private val environmentConfig: EnvironmentConfig,
    private val triplestoreService: TriplestoreService,
    private val roomService: RoomService
) {

    private val log : Logger = Logger.getLogger(RoomDistributionController::class.java.name)
    private val host = environmentConfig.getOrDefault("TRIPLESTORE_URL", "localhost")
    private val dataStore = environmentConfig.getOrDefault("TRIPLESTORE_DATASET", "Bedreflyt")
    private val tripleStore = "http://$host:3030/$dataStore"
    private val prefix = environmentConfig.getOrDefault("DOMAIN_PREFIX", "http://www.smolang.org/bedreflyt#")
    private val ttlPrefix = if (prefix.isNotEmpty()) prefix.dropLast(1) else prefix
    private val repl = replConfig.repl()

    @Operation(summary = "Add a room distribution")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room distribution added"),
        ApiResponse(responseCode = "400", description = "Invalid room distribution"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/create")
    fun addRoomDistribution(@SwaggerRequestBody(description = "Room distribution to add") @RequestBody roomDistributionRequest: RoomRequest) : ResponseEntity<String> {
        log.info("Adding room distribution")

        val bathroomInt = if (roomDistributionRequest.bathroom) 1 else 0
        if (!roomService.createRoom(
                roomDistributionRequest.roomNumber,
                roomDistributionRequest.roomNumberModel,
                roomDistributionRequest.room,
                roomDistributionRequest.capacity,
                bathroomInt)) {
            return ResponseEntity.badRequest().body("Error: the room distribution could not be added.")
        }
        replConfig.regenerateSingleModel().invoke("room distributions")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = """
            $fileContent
            
            ###  $ttlPrefix/room${roomDistributionRequest.roomNumber}
            :room${roomDistributionRequest.roomNumber} rdf:type owl:NamedIndividual ,
                            :Room ;
                :roomNumber ${roomDistributionRequest.roomNumber} ;
                :roomNumberModel ${roomDistributionRequest.roomNumberModel} ;
                :roomCategory ${roomDistributionRequest.room} ;
                :capacity ${roomDistributionRequest.capacity} ;
                :bathroom $bathroomInt .
        """.trimIndent()

        File(path).writeText(newContent)

        return ResponseEntity.ok("Room distribution added")
    }

    @Operation(summary = "Get all room distributions")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room distributions found"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/retrieve")
    fun getRoomDistributions() : ResponseEntity<List<Any>> {
        log.info("Retrieving room distributions")
        val roomDistributions = roomService.getAllRooms()?: return ResponseEntity.badRequest().body(listOf("No room distributions found"))
        return ResponseEntity.ok(roomDistributions)
    }

    @Operation(summary = "Update a room distribution")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room distribution updated"),
        ApiResponse(responseCode = "400", description = "Invalid room distribution"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/update")
    fun updateRoomDistribution(@SwaggerRequestBody(description = "Request to update a room distribution") @RequestBody updateRoomDistributionRequest: UpdateRoomRequest) : ResponseEntity<String> {
        log.info("Updating room distribution")

        val oldBath = if (updateRoomDistributionRequest.oldBathroom) 1 else 0
        val newBath = if (updateRoomDistributionRequest.newBathroom) 1 else 0
        if(!roomService.updateRoom(
                updateRoomDistributionRequest.oldRoomNumber,
                updateRoomDistributionRequest.oldRoomNumberModel,
                updateRoomDistributionRequest.oldRoom,
                updateRoomDistributionRequest.oldCapacity,
                oldBath,
                updateRoomDistributionRequest.newRoomNumber,
                updateRoomDistributionRequest.newRoomNumberModel,
                updateRoomDistributionRequest.newRoom,
                updateRoomDistributionRequest.newCapacity,
                newBath)) {
            return ResponseEntity.badRequest().body("Error: the room distribution could not be updated.")
        }
        replConfig.regenerateSingleModel().invoke("room distributions")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/room${updateRoomDistributionRequest.oldRoomNumber}
            :room${updateRoomDistributionRequest.oldRoomNumber} rdf:type owl:NamedIndividual ,
                            :Room ;
                :roomNumber ${updateRoomDistributionRequest.oldRoomNumber} ;
                :roomNumberModel ${updateRoomDistributionRequest.oldRoomNumberModel} ;
                :roomCategory ${updateRoomDistributionRequest.oldRoom} ;
                :capacity ${updateRoomDistributionRequest.oldCapacity} ;
                :bathroom $oldBath .
        """.trimIndent()
        val newContent = """
            ###  $ttlPrefix/room${updateRoomDistributionRequest.newRoomNumber}
            :room${updateRoomDistributionRequest.newRoomNumber} rdf:type owl:NamedIndividual ,
                            :Room ;
                :roomNumber ${updateRoomDistributionRequest.newRoomNumber} ;
                :roomNumberModel ${updateRoomDistributionRequest.newRoomNumberModel} ;
                :roomCategory ${updateRoomDistributionRequest.newRoom} ;
                :capacity ${updateRoomDistributionRequest.newCapacity} ;
                :bathroom $newBath .
        """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, newContent)

        return ResponseEntity.ok("Room distribution updated")
    }

    @Operation(summary = "Delete a room distribution")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room distribution deleted"),
        ApiResponse(responseCode = "400", description = "Invalid room distribution"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/delete")
    fun deleteRoomDistribution(@SwaggerRequestBody(description = "Request to delete a room distribution") @RequestBody roomDistributionRequest: RoomRequest) : ResponseEntity<String> {
        log.info("Deleting room distribution")

        val bath = if (roomDistributionRequest.bathroom) 1 else 0
        if(!roomService.deleteRoom(
                roomDistributionRequest.roomNumber,
                roomDistributionRequest.roomNumberModel,
                roomDistributionRequest.room,
                roomDistributionRequest.capacity,
                bath)) {
            return ResponseEntity.badRequest().body("Error: the room distribution could not be deleted.")
        }
        replConfig.regenerateSingleModel().invoke("room distributions")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/room${roomDistributionRequest.roomNumber}
            :room${roomDistributionRequest.roomNumber} rdf:type owl:NamedIndividual ,
                            :Room ;
                :roomNumber ${roomDistributionRequest.roomNumber} ;
                :roomNumberModel ${roomDistributionRequest.roomNumberModel} ;
                :roomCategory ${roomDistributionRequest.room} ;
                :capacity ${roomDistributionRequest.capacity} ;
                :bathroom $bath .
        """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, "")

        return ResponseEntity.ok("Room distribution deleted")
    }
}