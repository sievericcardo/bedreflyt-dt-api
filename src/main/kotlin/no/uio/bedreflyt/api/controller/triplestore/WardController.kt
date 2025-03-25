package no.uio.bedreflyt.api.controller.triplestore

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Ward
import no.uio.bedreflyt.api.service.triplestore.*
import no.uio.bedreflyt.api.types.WardRequest
import no.uio.bedreflyt.api.types.DeleteWardRequest
import no.uio.bedreflyt.api.types.UpdateWardRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.util.logging.Logger
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/fuseki/wards")
class WardController (
    private val replConfig: REPLConfig,
    private val environmentConfig: EnvironmentConfig,
    private val triplestoreService: TriplestoreService,
    private val hospitalService: HospitalService,
    private val cityService: CityService,
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
    @PostMapping
    fun createWard(@SwaggerRequestBody(description = "Request to add a new ward") @RequestBody wardRequest: WardRequest) : ResponseEntity<Ward> {
        log.info("Creating ward $wardRequest")

        val hospital = hospitalService.getHospitalByCode(wardRequest.wardHospitalName) ?: return ResponseEntity.badRequest().build()
        val floor = floorService.getFloorByNumber(wardRequest.wardFloorNumber) ?: return ResponseEntity.badRequest().build()
        if (!wardService.createWard(wardRequest)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("ward")

        return ResponseEntity.ok(Ward(wardRequest.wardName, wardRequest.wardCode, hospital, floor))
    }

    @Operation(summary = "Get all wards")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Wards found"),
        ApiResponse(responseCode = "400", description = "No wards found"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping
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
    @GetMapping("/{wardName}/{hospitalCode}")
    fun retrieveWard(@SwaggerRequestBody(description = "Request to retrieve a ward by name and hospital") @RequestBody wardRequest: WardRequest) : ResponseEntity<Ward> {
        log.info("Retrieving ward $wardRequest")

        val ward = wardService.getWardByNameAndHospital(wardRequest.wardName, wardRequest.wardHospitalName) ?: return ResponseEntity.badRequest().build()
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
    @PatchMapping
    fun updateWard(@SwaggerRequestBody(description = "Request to update a ward") @RequestBody request: UpdateWardRequest) : ResponseEntity<Ward> {
        log.info("Updating ward ${request.wardName}")

        val ward = wardService.getWardByNameAndHospital(request.wardName, request.hospitalName) ?: return ResponseEntity.badRequest().build()
        val hospital = hospitalService.getHospitalByCode(request.hospitalName) ?: return ResponseEntity.badRequest().build()
        val floor = request.newWardFloorNumber
        val newFloor = floorService.getFloorByNumber(floor) ?: return ResponseEntity.badRequest().build()
        if (!wardService.updateWard(ward, floor)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("ward")

        return ResponseEntity.ok(Ward(ward.wardName, ward.wardCode, hospital, newFloor))
    }

    @Operation(summary = "Delete a ward")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Ward deleted"),
        ApiResponse(responseCode = "400", description = "Invalid ward"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping
    fun deleteWard(@SwaggerRequestBody(description = "Request to delete a ward") @RequestBody request: DeleteWardRequest) : ResponseEntity<String> {
        log.info("Deleting ward ${request.wardName}")

        val ward = wardService.getWardByNameAndHospital(request.wardName, request.hospitalName) ?: return ResponseEntity.badRequest().build()
        if (!wardService.deleteWard(ward)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("ward")

        return ResponseEntity.ok("Ward ${request.wardName} deleted")
    }
}