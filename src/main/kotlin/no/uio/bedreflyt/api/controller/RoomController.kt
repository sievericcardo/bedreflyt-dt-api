package no.uio.bedreflyt.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.model.live.Room
import no.uio.bedreflyt.api.model.live.RoomDistribution
import no.uio.bedreflyt.api.service.live.RoomDistributionService
import no.uio.bedreflyt.api.service.live.RoomService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.logging.Logger

data class RoomRequest (
    val categoryNumber: Long,
    val roomDescription: String,
)

data class RoomDistributionRequest (
    val roomNumber: Long,
    val roomCategory: Long,
    val roomCapacity: Int,
    val bathroom: Boolean
)

@RestController
@RequestMapping("/api/room")
class RoomController (
    private val roomService : RoomService,
    private val roomDistributionService : RoomDistributionService
) {

    private val log : Logger = Logger.getLogger(HomeController::class.java.name)

    @Operation(summary = "Create a new room")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room created"),
        ApiResponse(responseCode = "400", description = "Invalid room"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/create")
    fun createRoom(@SwaggerRequestBody(description = "Request to add a new room") @RequestBody roomRequest: RoomRequest) : String {
        log.info("Creating room")

        if (roomRequest.roomDescription.isEmpty()) {
            return "Room information are required"
        }

        val room = Room(
            id = roomRequest.categoryNumber,
            roomDescription = roomRequest.roomDescription
        )

        roomService.saveRoom(room)

        return "Room created"
    }

    @Operation(summary = "Create a new room distribution")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room distribution created"),
        ApiResponse(responseCode = "400", description = "Invalid room distribution"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/createDistribution")
    fun createRoomDistribution(@SwaggerRequestBody(description = "Request to add a new room distribution") @RequestBody roomDistributionRequest: RoomDistributionRequest) : String {
        log.info("Creating room distribution")

        val room = roomService.findByRoomDescription(roomDistributionRequest.roomNumber.toString())
        if (room == null) {
            return "Room not found"
        }

        val roomDistribution = RoomDistribution(
            roomNumber = roomDistributionRequest.roomNumber,
            room = room,
            capacity = roomDistributionRequest.roomCapacity,
            bathroom = roomDistributionRequest.bathroom
        )

        roomDistributionService.saveRoomDistribution(roomDistribution)

        return "Room distribution created"
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
    fun deleteRoom(@SwaggerRequestBody(description = "Request to delete a room") @RequestBody roomRequest: RoomRequest) : String {
        log.info("Deleting room")

        if (roomRequest.roomDescription.isEmpty()) {
            return "Room information are required"
        }

        val room = roomService.findByRoomDescription(roomRequest.roomDescription)
        if (room == null) {
            return "Room not found"
        }

        roomService.deleteRoom(room)

        return "Room deleted"
    }

    @Operation(summary = "Delete a room distribution")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room distribution deleted"),
        ApiResponse(responseCode = "400", description = "Invalid room distribution"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/deleteDistribution")
    fun deleteRoomDistribution(@SwaggerRequestBody(description = "Request to delete a room distribution") @RequestBody roomDistributionRequest: RoomDistributionRequest) : String {
        log.info("Deleting room distribution")

        val roomDistribution = roomDistributionService.findByRoomNumber(roomDistributionRequest.roomNumber)
        if (roomDistribution == null) {
            return "Room distribution not found"
        }

        roomDistributionService.deleteRoomDistribution(roomDistribution)

        return "Room distribution deleted"
    }

    @Operation(summary = "Get all rooms")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Rooms found"),
        ApiResponse(responseCode = "400", description = "No rooms found"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/all")
    fun getAllRooms() : MutableList<Room?> {
        log.info("Getting all rooms")

        return roomService.findAll()
    }

    @Operation(summary = "Get all room distributions")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Room distributions found"),
        ApiResponse(responseCode = "400", description = "No room distributions found"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/allDistributions")
    fun getAllRoomDistributions() : MutableList<RoomDistribution?> {
        log.info("Getting all room distributions")

        return roomDistributionService.findAll()
    }
}