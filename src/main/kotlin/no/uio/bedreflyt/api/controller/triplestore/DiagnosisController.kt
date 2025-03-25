package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.annotations.ApiParam
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Diagnosis
import no.uio.bedreflyt.api.service.triplestore.DiagnosisService
import no.uio.bedreflyt.api.service.triplestore.TriplestoreService
import org.springframework.http.ResponseEntity
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import java.io.File
import java.util.logging.Logger
import no.uio.bedreflyt.api.types.DiagnosisRequest
import no.uio.bedreflyt.api.types.UpdateDiagnosisRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/fuseki/diagnosis")
class DiagnosisController (
    private val replConfig: REPLConfig,
    private val environmentConfig: EnvironmentConfig,
    private val triplestoreService: TriplestoreService,
    private val diagnosisService: DiagnosisService
) {

    private val log : Logger = Logger.getLogger(DiagnosisController::class.java.name)
    private val host = environmentConfig.getOrDefault("TRIPLESTORE_URL", "localhost")
    private val dataStore = environmentConfig.getOrDefault("TRIPLESTORE_DATASET", "Bedreflyt")
    private val tripleStore = "http://$host:3030/$dataStore"
    private val prefix = environmentConfig.getOrDefault("DOMAIN_PREFIX", "http://www.smolang.org/bedreflyt#")
    private val ttlPrefix = if (prefix.isNotEmpty()) prefix.dropLast(1) else prefix
    private val repl = replConfig.repl()

    @Operation(summary = "Add a new diagnosis")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Diagnosis added"),
        ApiResponse(responseCode = "400", description = "Invalid diagnosis"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping
    fun createDiagnosis (@SwaggerRequestBody(description = "Request to add a new patient") @RequestBody diagnosisRequest: DiagnosisRequest) : ResponseEntity<String> {
        log.info("Creating diagnosis $diagnosisRequest")

        if (!diagnosisService.createDiagnosis(diagnosisRequest.diagnosisName)) {
            return ResponseEntity.badRequest().body("Diagnosis already exists")
        }
        replConfig.regenerateSingleModel().invoke("diagnosis")

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
    @GetMapping
    fun retrieveDiagnosis() : ResponseEntity<List<Diagnosis>> {
        log.info("Retrieving diagnosis")
        val diagnosisList = diagnosisService.getAllDiagnosis() ?: return ResponseEntity.noContent().build()

        return ResponseEntity.ok(diagnosisList)
    }

    @Operation(summary = "Update a diagnosis")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Diagnosis updated"),
        ApiResponse(responseCode = "400", description = "Invalid diagnosis"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{diagnosisCode}")
    fun updateDiagnosis(@ApiParam(value = "Diagnosis code", required = true) @PathVariable diagnosisCode: String,
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
        replConfig.regenerateSingleModel().invoke("diagnosis")

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
    @DeleteMapping("/{diagnosisCode}")
    fun deleteDiagnosis(@ApiParam(value = "Diagnosis code", required = true) @PathVariable diagnosisCode: String) : ResponseEntity<String> {
        log.info("Deleting diagnosis $diagnosisCode")

        if(diagnosisCode.isEmpty()) {
            return ResponseEntity.badRequest().body("Diagnosis information are required")
        } else if (diagnosisService.getDiagnosisByName(diagnosisCode) == null) {
            return ResponseEntity.badRequest().body("Diagnosis does not exist")
        }
        if(!diagnosisService.deleteDiagnosis(diagnosisCode)) {
            return ResponseEntity.badRequest().body("Diagnosis does not exist")
        }
        replConfig.regenerateSingleModel().invoke("diagnosis")

        return ResponseEntity.ok("Diagnosis deleted")
    }
}