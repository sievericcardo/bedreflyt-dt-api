package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.annotations.ApiParam
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Room
import no.uio.bedreflyt.api.model.triplestore.TreatmentRoom
import no.uio.bedreflyt.api.service.triplestore.*
import org.springframework.http.ResponseEntity
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import java.io.File
import java.util.logging.Logger
import no.uio.bedreflyt.api.types.RoomRequest
import no.uio.bedreflyt.api.types.UpdateRoomRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/fuseki/rooms")
class RoomController (
    private val replConfig: REPLConfig,
    private val environmentConfig: EnvironmentConfig,
    private val triplestoreService: TriplestoreService,
    private val roomService: RoomService,
    private val wardService: WardService,
    private val hospitalService: HospitalService,
    private val monitoringCategoryService: MonitoringCategoryService
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
    @PostMapping
    fun createRoom (@SwaggerRequestBody(description = "Request to add a new room") @RequestBody roomRequest: RoomRequest) : ResponseEntity<TreatmentRoom> {
        log.info("Creating room $roomRequest")

        val ward = wardService.getWardByNameAndHospital(roomRequest.ward, roomRequest.hospital) ?: return ResponseEntity.badRequest().build()
        val hospital = hospitalService.getHospitalByCode(roomRequest.hospital) ?: return ResponseEntity.badRequest().build()
        val monitoringCategory = monitoringCategoryService.getCategoryByDescription(roomRequest.categoryDescription) ?: return ResponseEntity.badRequest().build()

        if (!roomService.createRoom(roomRequest)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("room")

        return ResponseEntity.ok(TreatmentRoom(roomRequest.roomNumber, roomRequest.capacity, ward, hospital, monitoringCategory))
    }

    @Operation(summary = "Get all rooms")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Rooms retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping
    fun retrieveRooms() : ResponseEntity<List<Room>> {
        log.info("Retrieving rooms")

        val rooms = roomService.getAllRooms() ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(rooms)
    }

    @Operation(summary = "Get a room by number, ward and hospital")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{roomNumber}/{wardName}/{hospitalCode}")
    fun retrieveRoom(@SwaggerRequestBody(description = "Request to retrieve a room by number, ward and hospital") @RequestBody roomRequest: RoomRequest) : ResponseEntity<TreatmentRoom> {
        log.info("Retrieving room $roomRequest")

        val room = roomService.getRoomByRoomNumberWardHospital(roomRequest.roomNumber, roomRequest.ward, roomRequest.hospital) ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(room)
    }

    @Operation(summary = "Update a room")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room updated"),
        ApiResponse(responseCode = "400", description = "Invalid room"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{roomNumber}/{wardName}/{hospitalCode}")
    fun updateRoom(@ApiParam(value = "Request to update a room", required = true) @PathVariable roomNumber: Int, wardName: String, hospitalCode: String,
                   @SwaggerRequestBody(description = "Request to update a room") @RequestBody updateRoomRequest: UpdateRoomRequest) : ResponseEntity<TreatmentRoom> {
        log.info("Updating room $updateRoomRequest")

        val room = roomService.getRoomByRoomNumberWardHospital(roomNumber, wardName, hospitalCode) ?: return ResponseEntity.notFound().build()
        val capacity = updateRoomRequest.newCapacity ?: room.capacity
        val ward = updateRoomRequest.newWard ?: room.treatmentWard.wardName
        val category = updateRoomRequest.newCategoryDescription ?: room.monitoringCategory.description

        val newWard = wardService.getWardByNameAndHospital(ward, room.hospital.hospitalCode) ?: return ResponseEntity.badRequest().build()
        val newCategory = monitoringCategoryService.getCategoryByDescription(category) ?: return ResponseEntity.badRequest().build()

        if (!roomService.updateRoom(room, capacity, ward, category)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("room")

        return ResponseEntity.ok(TreatmentRoom(room.roomNumber,capacity, newWard, room.hospital, newCategory))
    }

    @Operation(summary = "Delete a room")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room deleted"),
        ApiResponse(responseCode = "400", description = "Invalid room"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/{roomNumber}/{wardName}/{hospitalCode}")
    fun deleteRoom(@ApiParam(value = "Request to update a room", required = true) @PathVariable roomNumber: Int, wardName: String, hospitalCode: String) : ResponseEntity<String> {
        log.info("Deleting room $roomNumber in ward $wardName")

        val room = roomService.getRoomByRoomNumberWardHospital(roomNumber, wardName, hospitalCode) ?: return ResponseEntity.notFound().build()

        if (!roomService.deleteRoom(room)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("room")

        return ResponseEntity.ok("Room Deleted")
    }
}