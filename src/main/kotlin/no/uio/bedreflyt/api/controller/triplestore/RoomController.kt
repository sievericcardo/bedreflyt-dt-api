package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Room
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
import no.uio.bedreflyt.api.types.DeleteRoomRequest

@RestController
@RequestMapping("/api/fuseki/room")
class RoomController (
    private val replConfig: REPLConfig,
    private val environmentConfig: EnvironmentConfig,
    private val triplestoreService: TriplestoreService,
    private val roomService: RoomService
) {

    private val log : Logger = Logger.getLogger(RoomController::class.java.name)
    private val host = environmentConfig.getOrDefault("TRIPLESTORE_URL", "localhost")
    private val dataStore = environmentConfig.getOrDefault("TRIPLESTORE_DATASET", "Bedreflyt")
    private val tripleStore = "http://$host:3030/$dataStore"
    private val prefix = environmentConfig.getOrDefault("DOMAIN_PREFIX", "http://www.smolang.org/bedreflyt#")
    private val ttlPrefix = if (prefix.isNotEmpty()) prefix.dropLast(1) else prefix
    private val repl = replConfig.repl()

    @Operation(summary = "Add a room distribution")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room added"),
        ApiResponse(responseCode = "400", description = "Invalid room"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/create")
    fun addRoom(@SwaggerRequestBody(description = "Room distribution to add") @RequestBody roomDistributionRequest: RoomRequest) : ResponseEntity<String> {
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
        ApiResponse(responseCode = "200", description = "Room found"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/retrieve")
    fun getRooms() : ResponseEntity<List<Room>> {
        log.info("Retrieving room distributions")
        val roomDistributions = roomService.getAllRooms()?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(roomDistributions)
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
    fun updateRoom(@SwaggerRequestBody(description = "Request to update a room distribution") @RequestBody updateRoomRequest: UpdateRoomRequest) : ResponseEntity<String> {
        log.info("Updating room ${updateRoomRequest.roomNumber} with $updateRoomRequest")

        val room = roomService.getRoomByRoomNumber(updateRoomRequest.roomNumber) ?: return ResponseEntity.badRequest().body("Error: the room could not be updated.")
        val oldBath = if (room.bathroom) 1 else 0
        val newBath = if (updateRoomRequest.newBathroom != null) {
            if (updateRoomRequest.newBathroom) 1 else 0
        } else {
            oldBath
        }

        val newRoomNumberModel = updateRoomRequest.newRoomNumberModel ?: room.roomNumberModel
        val newRoomCategory = updateRoomRequest.newRoom ?: room.roomCategory
        val newCapacity = updateRoomRequest.newCapacity ?: room.capacity

        if(!roomService.updateRoom(
                room,
                newRoomNumberModel,
                newRoomCategory,
                newCapacity,
                newBath)) {
            return ResponseEntity.badRequest().body("Error: the room distribution could not be updated.")
        }
        replConfig.regenerateSingleModel().invoke("room distributions")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/room${updateRoomRequest.roomNumber}
            :room${updateRoomRequest.roomNumber} rdf:type owl:NamedIndividual ,
                            :Room ;
                :roomNumber ${room.roomNumber} ;
                :roomNumberModel ${room.roomNumberModel} ;
                :roomCategory ${room.roomCategory} ;
                :capacity ${room.capacity} ;
                :bathroom $oldBath .
        """.trimIndent()
        val newContent = """
            ###  $ttlPrefix/room${updateRoomRequest.roomNumber}
            :room${updateRoomRequest.roomNumber} rdf:type owl:NamedIndividual ,
                            :Room ;
                :roomNumber ${updateRoomRequest.roomNumber} ;
                :roomNumberModel $newRoomNumberModel ;
                :roomCategory $newRoomCategory ;
                :capacity $newCapacity ;
                :bathroom $newBath .
        """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, newContent)

        return ResponseEntity.ok("Room distribution updated")
    }

    @Operation(summary = "Update multiple room distribution")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Rooms updated"),
        ApiResponse(responseCode = "400", description = "Invalid rooms"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/update-multi")
    fun updateMultipleRooms(@SwaggerRequestBody(description = "Request to update a room distribution") @RequestBody updateRoomRequests: List<UpdateRoomRequest>) : ResponseEntity<String> {
        log.info("Updating ${updateRoomRequests.size} rooms")
        val rooms = mutableListOf<Room>()
        updateRoomRequests.forEach { updateRoomRequest ->
            val room = roomService.getRoomByRoomNumber(updateRoomRequest.roomNumber) ?: return ResponseEntity.badRequest().body("Error: the room ${updateRoomRequest.roomNumber} could not be updated.")
            rooms.add(room)
        }

        updateRoomRequests.forEach { updateRoomRequest ->
            val room = rooms.find { it.roomNumber == updateRoomRequest.roomNumber } ?: return ResponseEntity.badRequest().body("Error: the room could not be updated.")
            val oldBath = if (room.bathroom) 1 else 0
            val newBath = if (updateRoomRequest.newBathroom != null) {
                if (updateRoomRequest.newBathroom) 1 else 0
            } else {
                oldBath
            }

            val newRoomNumberModel = updateRoomRequest.newRoomNumberModel ?: room.roomNumberModel
            val newRoomCategory = updateRoomRequest.newRoom ?: room.roomCategory
            val newCapacity = updateRoomRequest.newCapacity ?: room.capacity

            if(!roomService.updateRoom(
                    room,
                    newRoomNumberModel,
                    newRoomCategory,
                    newCapacity,
                    newBath)) {
                return ResponseEntity.badRequest().body("Error: the room distribution could not be updated.")
            }

            // Append to the file bedreflyt.ttl
            val path = "bedreflyt.ttl"
            val oldContent = """
                ###  $ttlPrefix/room${updateRoomRequest.roomNumber}
                :room${updateRoomRequest.roomNumber} rdf:type owl:NamedIndividual ,
                                :Room ;
                    :roomNumber ${room.roomNumber} ;
                    :roomNumberModel ${room.roomNumberModel} ;
                    :roomCategory ${room.roomCategory} ;
                    :capacity ${room.capacity} ;
                    :bathroom $oldBath .
            """.trimIndent()
            val newContent = """
                ###  $ttlPrefix/room${updateRoomRequest.roomNumber}
                :room${updateRoomRequest.roomNumber} rdf:type owl:NamedIndividual ,
                                :Room ;
                    :roomNumber ${updateRoomRequest.roomNumber} ;
                    :roomNumberModel $newRoomNumberModel ;
                    :roomCategory $newRoomCategory ;
                    :capacity $newCapacity ;
                    :bathroom $newBath .
            """.trimIndent()

            triplestoreService.replaceContentIgnoringSpaces(path, oldContent, newContent)
        }

        replConfig.regenerateSingleModel().invoke("room distributions")

        return ResponseEntity.ok("Room distribution updated")
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
    fun deleteRoom(@SwaggerRequestBody(description = "Request to delete a room distribution") @RequestBody roomDeleteRequest: DeleteRoomRequest) : ResponseEntity<String> {
        log.info("Deleting room distribution")

        if(!roomService.deleteRoom(
                roomDeleteRequest.roomNumber)) {
            return ResponseEntity.badRequest().body("Error: the room distribution could not be deleted.")
        }
        replConfig.regenerateSingleModel().invoke("room distributions")

        val room = roomService.getRoomByRoomNumber(roomDeleteRequest.roomNumber)!!
        val bath = if (room.bathroom) 1 else 0

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/room${roomDeleteRequest.roomNumber}
            :room${roomDeleteRequest.roomNumber} rdf:type owl:NamedIndividual ,
                            :Room ;
                :roomNumber ${roomDeleteRequest.roomNumber} ;
                :roomNumberModel ${room.roomNumberModel} ;
                :roomCategory ${room.roomCategory} ;
                :capacity ${room.capacity} ;
                :bathroom $bath .
        """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, "")

        return ResponseEntity.ok("Room distribution deleted")
    }
}