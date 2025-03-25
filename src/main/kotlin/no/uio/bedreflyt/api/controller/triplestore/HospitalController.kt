package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Hospital
import no.uio.bedreflyt.api.service.triplestore.CityService
import no.uio.bedreflyt.api.service.triplestore.HospitalService
import no.uio.bedreflyt.api.service.triplestore.TriplestoreService
import no.uio.bedreflyt.api.types.HospitalRequest
import no.uio.bedreflyt.api.types.UpdateHospitalRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.util.logging.Logger
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/fuseki/hospitals")
class HospitalController (
    private val replConfig: REPLConfig,
    private val environmentConfig: EnvironmentConfig,
    private val triplestoreService: TriplestoreService,
    private val hospitalService: HospitalService,
    private val cityService: CityService
) {

    private val log: Logger = Logger.getLogger(HospitalController::class.java.name)

    @Operation(summary = "Add a new hospital")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Hospital added"),
        ApiResponse(responseCode = "400", description = "Invalid hospital"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping(produces= ["application/json"])
    fun createHospital(@SwaggerRequestBody(description = "Request to add a new hospital") @RequestBody hospitalRequest: HospitalRequest) : ResponseEntity<Hospital> {
        log.info("Creating hospital $hospitalRequest")

        if (!hospitalService.createHospital(hospitalRequest)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("hospitals")
        val city = cityService.getCityByName(hospitalRequest.city)

        return ResponseEntity.ok(Hospital(hospitalRequest.hospitalName, hospitalRequest.hospitalCode, city!!))
    }

    @Operation(summary = "Get all hospitals")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Hospitals retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping(produces= ["application/json"])
    fun retrieveHospitals() : ResponseEntity<List<Hospital>> {
        log.info("Retrieving hospitals")

        val hospitals = hospitalService.getAllHospitals() ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(hospitals)
    }

    @Operation(summary = "Get a hospital by code")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Hospital retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{hospitalCode}", produces= ["application/json"])
    fun retrieveHospitalByCode(@ApiParam(value = "Hospital code", required = true) @PathVariable hospitalCode: String) : ResponseEntity<Hospital> {
        log.info("Retrieving hospital $hospitalCode")

        val hospital = hospitalService.getHospitalByCode(hospitalCode) ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(hospital)
    }

    @Operation(summary = "Update a hospital")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Hospital updated"),
        ApiResponse(responseCode = "400", description = "Invalid hospital"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{hospitalCode}", produces= ["application/json"])
    fun updateHospital(@ApiParam(value = "Hospital code", required = true) @PathVariable hospitalCode: String,
                       @SwaggerRequestBody(description = "Request to update a hospital") @RequestBody updateHospitalRequest: UpdateHospitalRequest) : ResponseEntity<Hospital> {
        log.info("Updating hospital $updateHospitalRequest")

        val hospital = hospitalService.getHospitalByCode(hospitalCode) ?: return ResponseEntity.notFound().build()
        updateHospitalRequest.newHospitalName?.let {
            if (!hospitalService.updateHospital(hospital, it)) {
                return ResponseEntity.badRequest().build()
            }
        } ?: return ResponseEntity.noContent().build()
        replConfig.regenerateSingleModel().invoke("hospitals")

        return ResponseEntity.ok(Hospital(updateHospitalRequest.newHospitalName, hospital.hospitalCode, hospital.hospitalCity))
    }

    @Operation(summary = "Delete a hospital")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Hospital deleted"),
        ApiResponse(responseCode = "400", description = "Invalid hospital"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/{hospitalCode}", produces= ["application/json"])
    fun deleteHospital(@ApiParam(value = "Hospital code", required = true) @PathVariable hospitalCode: String) : ResponseEntity<String> {
        log.info("Deleting hospital $hospitalCode")

        if(hospitalService.getHospitalByCode(hospitalCode) == null) {
            return ResponseEntity.notFound().build()
        }
        if (!hospitalService.deleteHospital(hospitalCode)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("hospitals")

        return ResponseEntity.ok("Hospital deleted")
    }
}