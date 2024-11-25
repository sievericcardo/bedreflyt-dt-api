package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.RoomDistribution
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
@RequestMapping("/api/fuseki/roomDistribution")
class RoomDistributionController (
    private val replConfig: REPLConfig
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

        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :roomDistribution${roomDistributionRequest.roomNumber} a :RoomDistribution ;
                    :roomNumber ${roomDistributionRequest.roomNumber} ;
                    :roomNumberModel ${roomDistributionRequest.roomNumberModel} ;
                    :room ${roomDistributionRequest.room} ;
                    :capacity ${roomDistributionRequest.capacity} ;
                    :bathroom ${roomDistributionRequest.bathroom} .
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
            
            ###  http://$ttlPrefix/roomDistribution${roomDistributionRequest.roomNumber}
            :roomDistribution${roomDistributionRequest.roomNumber} rdf:type owl:NamedIndividual ,
                            :RoomDistribution ;
                :roomNumber ${roomDistributionRequest.roomNumber} ;
                :roomNumberModel ${roomDistributionRequest.roomNumberModel} ;
                :room ${roomDistributionRequest.room} ;
                :capacity ${roomDistributionRequest.capacity} ;
                :bathroom ${roomDistributionRequest.bathroom} .
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
        val query = """
            SELECT ?roomNumber ?roomNumberModel ?room ?capacity ?bathroom
            WHERE {
                ?obj a prog:RoomDistribution ;
                    prog:RoomDistribution_roomNumber ?roomNumber ;
                    prog:RoomDistribution_roomNumberModel ?roomNumberModel ;
                    prog:RoomDistribution_room ?room ;
                    prog:RoomDistribution_capacity ?capacity ;
                    prog:RoomDistribution_bathroom ?bathroom .
            }
        """.trimIndent()

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!

        val roomDistributions = mutableListOf<RoomDistribution>()

        if (!resultSet.hasNext()) {
            return ResponseEntity.badRequest().body(listOf("No room distributions found"))
        }
        while (resultSet.hasNext()) {
            val qs: QuerySolution = resultSet.next()
            val roomNumber = qs.get("roomNumber").asLiteral().toString().split("^^")[0].toInt()
            val roomNumberModel = qs.get("roomNumberModel").asLiteral().toString().split("^^")[0].toInt()
            val room = qs.get("room").asLiteral().toString().split("^^")[0].toLong()
            val capacity = qs.get("capacity").asLiteral().toString().split("^^")[0].toInt()
            val bathroom = qs.get("bathroom").asLiteral().toString().toBoolean()
            roomDistributions.add(RoomDistribution(roomNumber, roomNumberModel, room, capacity, bathroom))
        }

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

        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :roomDistribution${updateRoomDistributionRequest.oldRoomNumber} a :RoomDistribution ;
                    :roomNumber ${updateRoomDistributionRequest.oldRoomNumber} ;
                    :roomNumberModel ${updateRoomDistributionRequest.oldRoomNumberModel} ;
                    :room ${updateRoomDistributionRequest.oldRoom} ;
                    :capacity ${updateRoomDistributionRequest.oldCapacity} ;
                    :bathroom ${updateRoomDistributionRequest.oldBathroom} .
            }
            INSERT {
                :roomDistribution${updateRoomDistributionRequest.newRoomNumber} a :RoomDistribution ;
                    :roomNumber ${updateRoomDistributionRequest.newRoomNumber} ;
                    :roomNumberModel ${updateRoomDistributionRequest.newRoomNumberModel} ;
                    :room ${updateRoomDistributionRequest.newRoom} ;
                    :capacity ${updateRoomDistributionRequest.newCapacity} ;
                    :bathroom ${updateRoomDistributionRequest.newBathroom} .
            }
            WHERE {
                :roomDistribution${updateRoomDistributionRequest.oldRoomNumber} a :RoomDistribution ;
                    :roomNumber ${updateRoomDistributionRequest.oldRoomNumber} ;
                    :roomNumberModel ${updateRoomDistributionRequest.oldRoomNumberModel} ;
                    :room ${updateRoomDistributionRequest.oldRoom} ;
                    :capacity ${updateRoomDistributionRequest.oldCapacity} ;
                    :bathroom ${updateRoomDistributionRequest.oldBathroom} .
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
            ###  http://$ttlPrefix/roomDistribution${updateRoomDistributionRequest.oldRoomNumber}
            :roomDistribution${updateRoomDistributionRequest.oldRoomNumber} rdf:type owl:NamedIndividual ,
                            :RoomDistribution ;
                :roomNumber ${updateRoomDistributionRequest.oldRoomNumber} ;
                :roomNumberModel ${updateRoomDistributionRequest.oldRoomNumberModel} ;
                :room ${updateRoomDistributionRequest.oldRoom} ;
                :capacity ${updateRoomDistributionRequest.oldCapacity} ;
                :bathroom ${updateRoomDistributionRequest.oldBathroom} .
        """.trimIndent(), """
            ###  http://$ttlPrefix/roomDistribution${updateRoomDistributionRequest.newRoomNumber}
            :roomDistribution${updateRoomDistributionRequest.newRoomNumber} rdf:type owl:NamedIndividual ,
                            :RoomDistribution ;
                :roomNumber ${updateRoomDistributionRequest.newRoomNumber} ;
                :roomNumberModel ${updateRoomDistributionRequest.newRoomNumberModel} ;
                :room ${updateRoomDistributionRequest.newRoom} ;
                :capacity ${updateRoomDistributionRequest.newCapacity} ;
                :bathroom ${updateRoomDistributionRequest.newBathroom} .
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

        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :roomDistribution${roomDistributionRequest.roomNumber} a :RoomDistribution ;
                    :roomNumber ${roomDistributionRequest.roomNumber} ;
                    :roomNumberModel ${roomDistributionRequest.roomNumberModel} ;
                    :room ${roomDistributionRequest.room} ;
                    :capacity ${roomDistributionRequest.capacity} ;
                    :bathroom ${roomDistributionRequest.bathroom} .
            }
            WHERE {
                :roomDistribution${roomDistributionRequest.roomNumber} a :RoomDistribution ;
                    :roomNumber ${roomDistributionRequest.roomNumber} ;
                    :roomNumberModel ${roomDistributionRequest.roomNumberModel} ;
                    :room ${roomDistributionRequest.room} ;
                    :capacity ${roomDistributionRequest.capacity} ;
                    :bathroom ${roomDistributionRequest.bathroom} .
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
            ###  http://$ttlPrefix/roomDistribution${roomDistributionRequest.roomNumber}
            :roomDistribution${roomDistributionRequest.roomNumber} rdf:type owl:NamedIndividual ,
                            :RoomDistribution ;
                :roomNumber ${roomDistributionRequest.roomNumber} ;
                :roomNumberModel ${roomDistributionRequest.roomNumberModel} ;
                :room ${roomDistributionRequest.room} ;
                :capacity ${roomDistributionRequest.capacity} ;
                :bathroom ${roomDistributionRequest.bathroom} .
        """.trimIndent(), "")

        File(path).writeText(newContent)

        return ResponseEntity.ok("Room distribution deleted")
    }
}