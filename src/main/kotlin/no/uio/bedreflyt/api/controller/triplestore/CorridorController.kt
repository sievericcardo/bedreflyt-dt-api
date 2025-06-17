package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import no.uio.bedreflyt.api.model.triplestore.Corridor
import no.uio.bedreflyt.api.service.triplestore.CorridorService
import no.uio.bedreflyt.api.service.triplestore.MonitoringCategoryService
import no.uio.bedreflyt.api.service.triplestore.WardService
import no.uio.bedreflyt.api.types.CorridorRequest
import no.uio.bedreflyt.api.types.UpdateCorridorRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/fuseki/corridors")
class CorridorController (
    private val corridorService: CorridorService,
    private val wardService: WardService,
    private val monitoringCategoryService: MonitoringCategoryService
) {

    private val log: Logger = LoggerFactory.getLogger(CorridorController::class.java)

    @Operation(summary = "Add a new office")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Corridor added"),
        ApiResponse(responseCode = "400", description = "Invalid corridor"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping(produces= ["application/json"])
    fun createCorridor(@SwaggerRequestBody(description = "Request to add a new corridor") @Valid @RequestBody corridorRequest: CorridorRequest): ResponseEntity<Corridor> {
        log.info("Creating office $corridorRequest")

        wardService.getWardByNameAndHospital(corridorRequest.ward, corridorRequest.hospital) ?: return ResponseEntity.badRequest().build()
        monitoringCategoryService.getCategoryByDescription(corridorRequest.categoryDescription)
            ?: return ResponseEntity.badRequest().build()

        val newCorridor = corridorService.createCorridor(corridorRequest) ?: return ResponseEntity.badRequest().build()

        log.info("Office created successfully: $newCorridor")

        return ResponseEntity.ok(newCorridor)
    }

    @Operation(summary = "Get all corridors")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Corridors retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping(produces = ["application/json"])
    fun getAllCorridors(): ResponseEntity<List<Corridor>> {
        log.info("Retrieving all corridors")

        val corridors = corridorService.getAllCorridors() ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(corridors)
    }

    @Operation(summary = "Get corridor by room number, ward name, and hospital code")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Corridor retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{roomNumber}/{wardName}/{hospitalCode}", produces = ["application/json"])
    fun getCorridorByRoomNumberWardNameAndHospitalCode(
        @PathVariable roomNumber: Int,
        @PathVariable wardName: String,
        @PathVariable hospitalCode: String
    ): ResponseEntity<Corridor> {
        log.info("Retrieving corridor with room number $roomNumber, ward name $wardName, and hospital code $hospitalCode")

        val corridor = corridorService.getCorridorByRoonNumberWardHospital(roomNumber, wardName, hospitalCode)
            ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(corridor)
    }

    @Operation(summary = "Get corridors by ward name, and hospital code")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Corridors retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{wardName}/{hospitalCode}", produces = ["application/json"])
    fun getCorridorsByWardNameAndHospitalCode(
        @PathVariable wardName: String,
        @PathVariable hospitalCode: String
    ): ResponseEntity<List<Corridor>> {
        log.info("Retrieving corridors with ward name $wardName and hospital code $hospitalCode")

        val corridors = corridorService.getCorridorByWardHospital(wardName, hospitalCode)
            ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(corridors)
    }

    @Operation(summary = "Update a corridor")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Corridor updated"),
        ApiResponse(responseCode = "400", description = "Invalid corridor"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{roomNumber}/{wardName}/{hospitalCode}", produces = ["application/json"])
    fun updateCorridor(
        @PathVariable roomNumber: Int,
        @PathVariable wardName: String,
        @PathVariable hospitalCode: String,
        @SwaggerRequestBody(description = "Request to update an office") @Valid @RequestBody updateCorridorRequest: UpdateCorridorRequest
    ): ResponseEntity<Corridor> {
        log.info("Updating office with room number $roomNumber, ward name $wardName, and hospital code $hospitalCode")

        val corridor = corridorService.getCorridorByRoonNumberWardHospital(roomNumber, wardName, hospitalCode)
            ?: return ResponseEntity.badRequest().build()
        val ward = wardService.getWardByNameAndHospital(wardName, hospitalCode)
            ?: return ResponseEntity.badRequest().build()

        val capacity = updateCorridorRequest.newCapacity ?: corridor.capacity
        val penalty = updateCorridorRequest.newPenalty ?: corridor.penalty
        val wardName = updateCorridorRequest.newWard ?: ward.wardName
        val monitoringCategory = updateCorridorRequest.newCategoryDescription ?: corridor.monitoringCategory.description

        val updatedCorridor = corridorService.updateCorridor(corridor, capacity, penalty, wardName, monitoringCategory)
            ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(updatedCorridor)
    }

    @Operation(summary = "Delete an corridor")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Corridor deleted"),
        ApiResponse(responseCode = "400", description = "Invalid corridor"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/{roomNumber}/{wardName}/{hospitalCode}", produces = ["application/json"])
    fun deleteCorridor(
        @PathVariable roomNumber: Int,
        @PathVariable wardName: String,
        @PathVariable hospitalCode: String
    ): ResponseEntity<String> {
        log.info("Deleting corridor with room number $roomNumber, ward name $wardName, and hospital code $hospitalCode")

        val corridor = corridorService.getCorridorByRoonNumberWardHospital(roomNumber, wardName, hospitalCode)
            ?: return ResponseEntity.badRequest().build()

        corridorService.deleteCorridor(corridor)

        return ResponseEntity.ok("Corridor deleted")
    }
}