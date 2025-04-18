package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.annotations.ApiParam
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Ward
import no.uio.bedreflyt.api.service.triplestore.FloorService
import no.uio.bedreflyt.api.service.triplestore.HospitalService
import no.uio.bedreflyt.api.service.triplestore.WardService
import no.uio.bedreflyt.api.types.UpdateWardRequest
import no.uio.bedreflyt.api.types.WardRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RequestBody
import java.util.logging.Logger
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/fuseki/wards")
class WardController (
    private val replConfig: REPLConfig,
    private val hospitalService: HospitalService,
    private val wardService: WardService,
    private val floorService: FloorService
) {

    private val log: Logger = Logger.getLogger(WardController::class.java.name)

    @Operation(summary = "Add a new ward")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Ward added"),
        ApiResponse(responseCode = "400", description = "Invalid ward"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping(produces= ["application/json"])
    fun createWard(@SwaggerRequestBody(description = "Request to add a new ward") @Valid @RequestBody wardRequest: WardRequest) : ResponseEntity<Ward> {
        log.info("Creating ward $wardRequest")

        val hospital = hospitalService.getHospitalByCode(wardRequest.wardHospitalName) ?: return ResponseEntity.badRequest().build()
        val floor = floorService.getFloorByNumber(wardRequest.wardFloorNumber) ?: return ResponseEntity.badRequest().build()
        val newWard = wardService.getWardByNameAndHospital(wardRequest.wardName, hospital.hospitalName)
        replConfig.regenerateSingleModel().invoke("wards")

        return ResponseEntity.ok(newWard)
    }

    @Operation(summary = "Get all wards")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Wards found"),
        ApiResponse(responseCode = "400", description = "No wards found"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping(produces= ["application/json"])
    fun retrieveWards() : ResponseEntity<List<Ward>> {
        log.info("Retrieving wards")

        val wards = wardService.getAllWards() ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(wards)
    }

    @Operation(summary = "Get a ward by ward name and hospital")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Ward found"),
        ApiResponse(responseCode = "400", description = "No ward found"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{wardName}/{hospitalCode}", produces= ["application/json"])
    fun retrieveWard(@ApiParam(value = "Ward name", required = true) @Valid @PathVariable wardName: String,
                     @ApiParam(value = "Hospital code for the ward", required = true) @Valid @PathVariable hospitalCode: String) : ResponseEntity<Ward> {
        log.info("Retrieving ward $wardName in hospital $hospitalCode")

        val ward = wardService.getWardByNameAndHospital(wardName, hospitalCode) ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(ward)
    }

    @Operation(summary = "Update a ward")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Ward updated"),
        ApiResponse(responseCode = "400", description = "Invalid ward"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{wardName}/{hospitalCode}", produces= ["application/json"])
    fun updateWard(@ApiParam(value = "Ward name", required = true) @PathVariable wardName: String,
                   @ApiParam(value = "Hospital code for the ward", required = true) @PathVariable hospitalCode: String,
                   @SwaggerRequestBody(description = "Request to update a ward") @RequestBody request: UpdateWardRequest) : ResponseEntity<Ward> {
        log.info("Updating ward $wardName")

        val ward = wardService.getWardByNameAndHospital(wardName, hospitalCode) ?: return ResponseEntity.notFound().build()
        val hospital = hospitalService.getHospitalByCode(hospitalCode) ?: return ResponseEntity.notFound().build()
        val updatedWard = request.newWardFloorNumber?.let {
            floorService.getFloorByNumber(it) ?: return ResponseEntity.notFound().build()
            wardService.updateWard(ward, it) ?: return ResponseEntity.badRequest().build()
        } ?: return ResponseEntity.noContent().build()
        replConfig.regenerateSingleModel().invoke("wards")

        return ResponseEntity.ok(updatedWard)
    }

    @Operation(summary = "Delete a ward")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Ward deleted"),
        ApiResponse(responseCode = "400", description = "Invalid ward"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/{wardName}/{hospitalCode}", produces= ["application/json"])
    fun deleteWard(@ApiParam(value = "Ward name", required = true) @Valid @PathVariable wardName: String,
                   @ApiParam(value = "Hospital code for the ward", required = true) @Valid @PathVariable hospitalCode: String) : ResponseEntity<String> {
        log.info("Deleting ward $wardName in hospital $hospitalCode")

        val ward = wardService.getWardByNameAndHospital(wardName, hospitalCode) ?: return ResponseEntity.notFound().build()
        if (!wardService.deleteWard(ward)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("wards")

        return ResponseEntity.ok("Ward $wardName deleted")
    }
}