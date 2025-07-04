package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.annotations.ApiParam
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Room
import no.uio.bedreflyt.api.model.triplestore.TreatmentRoom
import no.uio.bedreflyt.api.service.triplestore.*
import no.uio.bedreflyt.api.types.RoomRequest
import no.uio.bedreflyt.api.types.UpdateRoomRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/fuseki/rooms")
class RoomController(
    private val replConfig: REPLConfig,
    environmentConfig: EnvironmentConfig,
    private val roomService: RoomService,
    private val wardService: WardService,
    private val hospitalService: HospitalService,
    private val monitoringCategoryService: MonitoringCategoryService
) {

    private val log : Logger = LoggerFactory.getLogger(RoomController::class.java.name)

    @Operation(summary = "Add a room distribution")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room added"),
        ApiResponse(responseCode = "400", description = "Invalid room"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping(produces= ["application/json"])
    fun createRoom (@SwaggerRequestBody(description = "Request to add a new room") @Valid @RequestBody roomRequest: RoomRequest) : ResponseEntity<TreatmentRoom> {
        log.info("Creating room $roomRequest")

        wardService.getWardByNameAndHospital(roomRequest.ward, roomRequest.hospital) ?: return ResponseEntity.badRequest().build()
        hospitalService.getHospitalByCode(roomRequest.hospital) ?: return ResponseEntity.badRequest().build()
        monitoringCategoryService.getCategoryByDescription(roomRequest.categoryDescription) ?: return ResponseEntity.badRequest().build()
        val newRoom = roomService.createRoom(roomRequest) ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(newRoom)
    }

    @Operation(summary = "All rooms created")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Rooms added"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/multi", produces= ["application/json"])
    fun createMultiRooms(@SwaggerRequestBody(description = "Request to add multiple rooms") @RequestBody roomRequests: List<RoomRequest>) : ResponseEntity<List<TreatmentRoom>> {
        log.info("Creating multiple rooms $roomRequests")

        roomRequests.forEach { request ->
            wardService.getWardByNameAndHospital(request.ward, request.hospital) ?: return ResponseEntity.badRequest().build()
            hospitalService.getHospitalByCode(request.hospital) ?: return ResponseEntity.badRequest().build()
            monitoringCategoryService.getCategoryByDescription(request.categoryDescription) ?: return ResponseEntity.badRequest().build()
        }
        val createdRooms = roomService.createMultiRooms(roomRequests) ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(createdRooms)
    }

    @Operation(summary = "Get all rooms")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Rooms retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping(produces= ["application/json"])
    fun retrieveRooms() : ResponseEntity<List<Room>> {
        log.info("Retrieving rooms")

        val rooms = roomService.getAllRooms() ?: return ResponseEntity.noContent().build()

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
    @GetMapping("/{roomNumber}/{wardName}/{hospitalCode}", produces= ["application/json"])
    fun retrieveRoom(@ApiParam(value = "Room number", required = true) @Valid @PathVariable roomNumber: Int,
                     @ApiParam(value = "Ward name", required = true) @Valid @PathVariable wardName: String,
                     @ApiParam(value = "Hospital code", required = true) @Valid @PathVariable hospitalCode: String) : ResponseEntity<TreatmentRoom> {
        log.info("Retrieving room $roomNumber in ward $wardName")

        val room = roomService.getRoomByRoomNumberWardHospital(roomNumber, wardName, hospitalCode) ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(room)
    }

    @Operation(summary = "Get a room by number, ward and hospital")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{wardName}/{hospitalCode}", produces= ["application/json"])
    fun getRoomByWardAndHospital(
            @ApiParam(value = "Ward name", required = true) @Valid @PathVariable wardName: String,
            @ApiParam(value = "Hospital code", required = true) @Valid @PathVariable hospitalCode: String
    ) : ResponseEntity<List<TreatmentRoom>> {
        log.info("Retrieving rooms in ward $wardName")

        val rooms = roomService.getRoomsByWardHospital(wardName, hospitalCode) ?: return ResponseEntity.badRequest().build()

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
    @PatchMapping("/{roomNumber}/{wardName}/{hospitalCode}", produces= ["application/json"])
    fun updateRoom(@ApiParam(value = "Room number", required = true) @Valid @PathVariable roomNumber: Int,
                   @ApiParam(value = "Ward name", required = true) @Valid @PathVariable wardName: String,
                   @ApiParam(value = "Hospital code", required = true) @Valid @PathVariable hospitalCode: String,
                   @SwaggerRequestBody(description = "Request to update a room") @Valid @RequestBody updateRoomRequest: UpdateRoomRequest) : ResponseEntity<TreatmentRoom> {
        log.info("Updating room $updateRoomRequest")

        val room = roomService.getRoomByRoomNumberWardHospital(roomNumber, wardName, hospitalCode) ?: return ResponseEntity.notFound().build()
        val capacity = updateRoomRequest.newCapacity ?: room.capacity
        val ward = updateRoomRequest.newWard ?: room.treatmentWard.wardName
        val category = updateRoomRequest.newCategoryDescription ?: room.monitoringCategory.description

        val updatedRoom = roomService.updateRoom(room, capacity, ward, category) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(updatedRoom)
    }

    @Operation(summary = "Delete a room")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room deleted"),
        ApiResponse(responseCode = "400", description = "Invalid room"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/{roomNumber}/{wardName}/{hospitalCode}", produces= ["application/json"])
    fun deleteRoom(@ApiParam(value = "Room number", required = true) @Valid @PathVariable roomNumber: Int,
                   @ApiParam(value = "Ward name", required = true) @Valid @PathVariable wardName: String,
                   @ApiParam(value = "Hospital code", required = true) @Valid @PathVariable hospitalCode: String) : ResponseEntity<String> {
        log.info("Deleting room $roomNumber in ward $wardName")

        val room = roomService.getRoomByRoomNumberWardHospital(roomNumber, wardName, hospitalCode) //?: return ResponseEntity.notFound().build()

        if (!roomService.deleteRoom(room!!)) {
            return ResponseEntity.badRequest().build()
        }

        return ResponseEntity.ok("Room Deleted")
    }
}