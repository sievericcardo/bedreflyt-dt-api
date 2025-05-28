package no.uio.bedreflyt.api.controller.triplestore

import no.uio.bedreflyt.api.service.triplestore.OfficeService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import no.uio.bedreflyt.api.types.OfficeRequest
import no.uio.bedreflyt.api.types.UpdateOfficeRequest
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import no.uio.bedreflyt.api.model.triplestore.Office
import no.uio.bedreflyt.api.service.triplestore.MonitoringCategoryService
import no.uio.bedreflyt.api.service.triplestore.WardService
import org.springframework.http.ResponseEntity
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/fuseki/offices")
class OfficeController (
    private val officeService: OfficeService,
    private val wardService: WardService,
    private val monitoringCategoryService: MonitoringCategoryService
) {

    private val log: Logger = LoggerFactory.getLogger(OfficeController::class.java)

    @Operation(summary = "Add a new office")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Office added"),
        ApiResponse(responseCode = "400", description = "Invalid office"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping(produces= ["application/json"])
    fun createOffice(@SwaggerRequestBody(description = "Request to add a new office") @Valid @RequestBody officeRequest: OfficeRequest): ResponseEntity<Office> {
        log.info("Creating office $officeRequest")

        wardService.getWardByNameAndHospital(officeRequest.ward, officeRequest.hospital) ?: return ResponseEntity.badRequest().build()
        monitoringCategoryService.getCategoryByDescription(officeRequest.categoryDescription)
            ?: return ResponseEntity.badRequest().build()

        val newOffice = officeService.createOffice(officeRequest) ?: return ResponseEntity.badRequest().build()

        log.info("Office created successfully: $newOffice")

        return ResponseEntity.ok(newOffice)
    }

    @Operation(summary = "Get all offices")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Offices retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping(produces = ["application/json"])
    fun getAllOffices(): ResponseEntity<List<Office>> {
        log.info("Retrieving all offices")

        val offices = officeService.getAllOffices() ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(offices)
    }

    @Operation(summary = "Get office by room number, ward name, and hospital code")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Office retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{roomNumber}/{wardName}/{hospitalCode}", produces = ["application/json"])
    fun getOfficeByRoomNumberWardNameAndHospitalCode(
        @PathVariable roomNumber: Int,
        @PathVariable wardName: String,
        @PathVariable hospitalCode: String
    ): ResponseEntity<Office> {
        log.info("Retrieving office with room number $roomNumber, ward name $wardName, and hospital code $hospitalCode")

        val office = officeService.getOfficeByRoonNumberWardHospital(roomNumber, wardName, hospitalCode)
            ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(office)
    }

    @Operation(summary = "Get offices by ward name, and hospital code")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Offices retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{wardName}/{hospitalCode}", produces = ["application/json"])
    fun getOfficesByWardNameAndHospitalCode(
        @PathVariable wardName: String,
        @PathVariable hospitalCode: String
    ): ResponseEntity<List<Office>> {
        log.info("Retrieving offices with ward name $wardName and hospital code $hospitalCode")

        val offices = officeService.getOfficeByWardHospital(wardName, hospitalCode)
            ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(offices)
    }

    @Operation(summary = "Update an office")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Office updated"),
        ApiResponse(responseCode = "400", description = "Invalid office"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{roomNumber}/{wardName}/{hospitalCode}", produces = ["application/json"])
    fun updateOffice(
        @PathVariable roomNumber: Int,
        @PathVariable wardName: String,
        @PathVariable hospitalCode: String,
        @SwaggerRequestBody(description = "Request to update an office") @Valid @RequestBody updateOfficeRequest: UpdateOfficeRequest
    ): ResponseEntity<Office> {
        log.info("Updating office with room number $roomNumber, ward name $wardName, and hospital code $hospitalCode")

        val office = officeService.getOfficeByRoonNumberWardHospital(roomNumber, wardName, hospitalCode)
            ?: return ResponseEntity.badRequest().build()
        val ward = wardService.getWardByNameAndHospital(wardName, hospitalCode)
            ?: return ResponseEntity.badRequest().build()

        val capacity = updateOfficeRequest.newCapacity ?: office.capacity
        val available = updateOfficeRequest.newAvailable?: office.available
        val wardName = updateOfficeRequest.newWard ?: ward.wardName
        val monitoringCategory = updateOfficeRequest.newCategoryDescription ?: office.monitoringCategory.description

        val updatedOffice = officeService.updateOffice(office, capacity, available, wardName, monitoringCategory)
            ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(updatedOffice)
    }

    @Operation(summary = "Delete an office")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Office deleted"),
        ApiResponse(responseCode = "400", description = "Invalid office"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/{roomNumber}/{wardName}/{hospitalCode}", produces = ["application/json"])
    fun deleteOffice(
        @PathVariable roomNumber: Int,
        @PathVariable wardName: String,
        @PathVariable hospitalCode: String
    ): ResponseEntity<String> {
        log.info("Deleting office with room number $roomNumber, ward name $wardName, and hospital code $hospitalCode")

        val office = officeService.getOfficeByRoonNumberWardHospital(roomNumber, wardName, hospitalCode)
            ?: return ResponseEntity.badRequest().build()

        officeService.deleteOffice(office)

        return ResponseEntity.ok("Office deleted")
    }
}