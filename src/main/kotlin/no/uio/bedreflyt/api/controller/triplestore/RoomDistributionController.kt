package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.service.triplestore.TriplestoreService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.util.logging.Logger

data class RoomDistributionRequest (
    val roomNumber: Int,
    val roomNumberModel: Int,
    val room: Long,
    val capacity: Int,
    val bathroom: Boolean
)

data class UpdateRoomDistributionRequest (
    val oldRoomNumber: Int,
    val oldRoomNumberModel: Int,
    val oldRoom: Long,
    val oldCapacity: Int,
    val oldBathroom: Boolean,
    val newRoomNumber: Int,
    val newRoomNumberModel: Int,
    val newRoom: Long,
    val newCapacity: Int,
    val newBathroom: Boolean
)

@RestController
@RequestMapping("/api/fuseki/room-distribution")
class RoomDistributionController (
    private val replConfig: REPLConfig,
    private val triplestoreService: TriplestoreService
) {

    private val log : Logger = Logger.getLogger(RoomDistributionController::class.java.name)
    private val host = System.getenv().getOrDefault("TRIPLESTORE_URL", "localhost")
    private val dataStore = System.getenv().getOrDefault("TRIPLESTORE_DATASET", "Bedreflyt")
    private val tripleStore = "http://$host:3030/$dataStore"
    private val prefix = System.getenv().getOrDefault("DOMAIN_PREFIX", "http://www.smolang.org/bedreflyt#")
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
    fun addRoomDistribution(@SwaggerRequestBody(description = "Room distribution to add") @RequestBody roomDistributionRequest: RoomDistributionRequest) : ResponseEntity<String> {
        log.info("Adding room distribution")

        val bathroomInt = if (roomDistributionRequest.bathroom) 1 else 0
        if (!triplestoreService.createRoomDistribution(
                roomDistributionRequest.roomNumber,
                roomDistributionRequest.roomNumberModel,
                roomDistributionRequest.room,
                roomDistributionRequest.capacity,
                bathroomInt)) {
            return ResponseEntity.badRequest().body("Error: the room distribution could not be added.")
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
            
            ###  http://$ttlPrefix/roomDistribution${roomDistributionRequest.roomNumber}
            :roomDistribution${roomDistributionRequest.roomNumber} rdf:type owl:NamedIndividual ,
                            :RoomDistribution ;
                :roomNumber ${roomDistributionRequest.roomNumber} ;
                :roomNumberModel ${roomDistributionRequest.roomNumberModel} ;
                :room ${roomDistributionRequest.room} ;
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
        val roomDistributions = triplestoreService.getAllRoomDistributions()?: return ResponseEntity.badRequest().body(listOf("No room distributions found"))
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
    @PostMapping("/update")
    fun updateRoomDistribution(@SwaggerRequestBody(description = "Request to update a room distribution") @RequestBody updateRoomDistributionRequest: UpdateRoomDistributionRequest) : ResponseEntity<String> {
        log.info("Updating room distribution")

        val oldBath = if (updateRoomDistributionRequest.oldBathroom) 1 else 0
        val newBath = if (updateRoomDistributionRequest.newBathroom) 1 else 0
        if(!triplestoreService.updateRoomDistribution(
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

        repl.interpreter!!.tripleManager.regenerateTripleStoreModel()
        repl.interpreter!!.evalCall(
            repl.interpreter!!.getObjectNames("AssetModel")[0],
            "AssetModel",
            "reconfigure")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = fileContent.replace("""
            ###  http://$ttlPrefix/roomDistribution${updateRoomDistributionRequest.oldRoomNumber}
            :roomDistribution${updateRoomDistributionRequest.oldRoomNumber} rdf:type owl:NamedIndividual ,
                            :RoomDistribution ;
                :roomNumber ${updateRoomDistributionRequest.oldRoomNumber} ;
                :roomNumberModel ${updateRoomDistributionRequest.oldRoomNumberModel} ;
                :room ${updateRoomDistributionRequest.oldRoom} ;
                :capacity ${updateRoomDistributionRequest.oldCapacity} ;
                :bathroom $oldBath.
        """.trimIndent(), """
            ###  http://$ttlPrefix/roomDistribution${updateRoomDistributionRequest.newRoomNumber}
            :roomDistribution${updateRoomDistributionRequest.newRoomNumber} rdf:type owl:NamedIndividual ,
                            :RoomDistribution ;
                :roomNumber ${updateRoomDistributionRequest.newRoomNumber} ;
                :roomNumberModel ${updateRoomDistributionRequest.newRoomNumberModel} ;
                :room ${updateRoomDistributionRequest.newRoom} ;
                :capacity ${updateRoomDistributionRequest.newCapacity} ;
                :bathroom $newBath .
        """.trimIndent())

        File(path).writeText(newContent)

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
    @PostMapping("/delete")
    fun deleteRoomDistribution(@SwaggerRequestBody(description = "Request to delete a room distribution") @RequestBody roomDistributionRequest: RoomDistributionRequest) : ResponseEntity<String> {
        log.info("Deleting room distribution")

        val bath = if (roomDistributionRequest.bathroom) 1 else 0
        if(!triplestoreService.deleteRoomDistribution(
                roomDistributionRequest.roomNumber,
                roomDistributionRequest.roomNumberModel,
                roomDistributionRequest.room,
                roomDistributionRequest.capacity,
                bath)) {
            return ResponseEntity.badRequest().body("Error: the room distribution could not be deleted.")
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
            ###  http://$ttlPrefix/roomDistribution${roomDistributionRequest.roomNumber}
            :roomDistribution${roomDistributionRequest.roomNumber} rdf:type owl:NamedIndividual ,
                            :RoomDistribution ;
                :roomNumber ${roomDistributionRequest.roomNumber} ;
                :roomNumberModel ${roomDistributionRequest.roomNumberModel} ;
                :room ${roomDistributionRequest.room} ;
                :capacity ${roomDistributionRequest.capacity} ;
                :bathroom $bath .
        """.trimIndent(), "")

        File(path).writeText(newContent)

        return ResponseEntity.ok("Room distribution deleted")
    }
}