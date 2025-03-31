package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.annotations.ApiParam
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Diagnosis
import no.uio.bedreflyt.api.service.triplestore.DiagnosisService
import no.uio.bedreflyt.api.types.DiagnosisRequest
import no.uio.bedreflyt.api.types.UpdateDiagnosisRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.logging.Logger
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/fuseki/diagnosis")
class DiagnosisController (
    private val replConfig: REPLConfig,
    private val diagnosisService: DiagnosisService
) {

    private val log : Logger = Logger.getLogger(DiagnosisController::class.java.name)

    @Operation(summary = "Add a new diagnosis")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Diagnosis added"),
        ApiResponse(responseCode = "400", description = "Invalid diagnosis"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping(produces= ["application/json"])
    fun createDiagnosis (@SwaggerRequestBody(description = "Request to add a new patient") @Valid @RequestBody diagnosisRequest: DiagnosisRequest) : ResponseEntity<String> {
        log.info("Creating diagnosis $diagnosisRequest")

        if (!diagnosisService.createDiagnosis(diagnosisRequest.diagnosisName)) {
            return ResponseEntity.badRequest().body("Diagnosis already exists")
        }
        replConfig.regenerateSingleModel().invoke("diagnoses")

        return ResponseEntity.ok("Diagnosis added")
    }

    @Operation(summary = "Retrieve the diagnosis")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Diagnosis retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid diagnosis"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping(produces= ["application/json"])
    fun retrieveDiagnosis() : ResponseEntity<List<Diagnosis>> {
        log.info("Retrieving diagnosis")
        val diagnosisList = diagnosisService.getAllDiagnosis() ?: return ResponseEntity.noContent().build()

        return ResponseEntity.ok(diagnosisList)
    }

    @Operation(summary = "Get a diagnosis by code")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Diagnosis found"),
        ApiResponse(responseCode = "400", description = "Invalid diagnosis"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{diagnosisCode}", produces= ["application/json"])
    fun retrieveDiagnosisByCode(@ApiParam(value = "Diagnosis code", required = true) @Valid @PathVariable diagnosisCode: String) : ResponseEntity<Diagnosis> {
        log.info("Retrieving diagnosis $diagnosisCode")

        val diagnosis = diagnosisService.getDiagnosisByName(diagnosisCode) ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(diagnosis)
    }

    @Operation(summary = "Update a diagnosis")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Diagnosis updated"),
        ApiResponse(responseCode = "400", description = "Invalid diagnosis"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{diagnosisCode}", produces= ["application/json"])
    fun updateDiagnosis(@ApiParam(value = "Diagnosis code", required = true) @Valid @PathVariable diagnosisCode: String,
                        @SwaggerRequestBody(description = "Request to update a diagnosis") @RequestBody updateDiagnosisRequest: UpdateDiagnosisRequest) : ResponseEntity<Diagnosis> {
        log.info("Updating diagnosis $updateDiagnosisRequest")

        if(diagnosisService.getDiagnosisByName(diagnosisCode) == null) {
            return ResponseEntity.notFound().build()
        }
        updateDiagnosisRequest.newDiagnosisName?.let {
            if(!diagnosisService.updateDiagnosis(diagnosisCode, it)) {
                return ResponseEntity.badRequest().build()
            }
        } ?: return ResponseEntity.noContent().build()
        replConfig.regenerateSingleModel().invoke("diagnoses")

        return ResponseEntity.ok(Diagnosis(updateDiagnosisRequest.newDiagnosisName))
    }

    @Operation(summary = "Delete a diagnosis")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Diagnosis deleted"),
        ApiResponse(responseCode = "400", description = "Invalid diagnosis"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/{diagnosisCode}", produces= ["application/json"])
    fun deleteDiagnosis(@ApiParam(value = "Diagnosis code", required = true) @Valid @PathVariable diagnosisCode: String) : ResponseEntity<String> {
        log.info("Deleting diagnosis $diagnosisCode")

        if(diagnosisCode.isEmpty()) {
            return ResponseEntity.badRequest().body("Diagnosis information are required")
        } else if (diagnosisService.getDiagnosisByName(diagnosisCode) == null) {
            return ResponseEntity.badRequest().body("Diagnosis does not exist")
        }
        if(!diagnosisService.deleteDiagnosis(diagnosisCode)) {
            return ResponseEntity.badRequest().body("Diagnosis does not exist")
        }
        replConfig.regenerateSingleModel().invoke("diagnoses")

        return ResponseEntity.ok("Diagnosis deleted")
    }
}