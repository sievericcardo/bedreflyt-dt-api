package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Diagnosis
import no.uio.bedreflyt.api.service.triplestore.DiagnosisService
import no.uio.bedreflyt.api.service.triplestore.TriplestoreService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.util.logging.Logger
import no.uio.bedreflyt.api.types.DiagnosisRequest
import no.uio.bedreflyt.api.types.UpdateDiagnosisRequest

@RestController
@RequestMapping("/api/fuseki/diagnosis")
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
    @PostMapping("/create")
    fun createDiagnosis (@SwaggerRequestBody(description = "Request to add a new patient") @RequestBody diagnosisRequest: DiagnosisRequest) : ResponseEntity<String> {
        log.info("Creating diagnosis $diagnosisRequest")

        if (!diagnosisService.createDiagnosis(diagnosisRequest.diagnosisName)) {
            return ResponseEntity.badRequest().body("Diagnosis already exists")
        }
        replConfig.regenerateSingleModel().invoke("diagnosis")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = """
            $fileContent
            
            ###  $ttlPrefix/diagnosis_${diagnosisRequest.diagnosisName}
            :diagnosis_${diagnosisRequest.diagnosisName} rdf:type owl:NamedIndividual ,
                            :Diagnosis ;
                :diagnosisName "${diagnosisRequest.diagnosisName}" .
        """.trimIndent()

        File(path).writeText(newContent)

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
    @GetMapping("/retrieve")
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
    @PatchMapping("/update")
    fun updateDiagnosis(@SwaggerRequestBody(description = "Request to update a diagnosis") @RequestBody updateDiagnosisRequest: UpdateDiagnosisRequest) : ResponseEntity<String> {
        log.info("Updating diagnosis $updateDiagnosisRequest")

        if (updateDiagnosisRequest.oldDiagnosisName.isEmpty() || updateDiagnosisRequest.newDiagnosisName.isEmpty()) {
            return ResponseEntity.badRequest().body("Diagnosis name cannot be empty")
        }

        if(!diagnosisService.updateDiagnosis(updateDiagnosisRequest.oldDiagnosisName, updateDiagnosisRequest.newDiagnosisName)) {
            return ResponseEntity.badRequest().body("Diagnosis does not exist")
        }
        replConfig.regenerateSingleModel().invoke("diagnosis")

        // Update the object in the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/diagnosis_${updateDiagnosisRequest.oldDiagnosisName}
            :diagnosis_${updateDiagnosisRequest.oldDiagnosisName} rdf:type owl:NamedIndividual ,
                            :Diagnosis ;
                :diagnosisName "${updateDiagnosisRequest.oldDiagnosisName}" .
            """.trimIndent()
        val newContent = """
            ###  $ttlPrefix/diagnosis_${updateDiagnosisRequest.newDiagnosisName}
            :diagnosis_${updateDiagnosisRequest.newDiagnosisName} rdf:type owl:NamedIndividual ,
                            :Diagnosis ;
                :diagnosisName "${updateDiagnosisRequest.newDiagnosisName}" .
            """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, newContent)

        return ResponseEntity.ok("Diagnosis updated")
    }

    @Operation(summary = "Delete a diagnosis")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Diagnosis deleted"),
        ApiResponse(responseCode = "400", description = "Invalid diagnosis"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/delete")
    fun deleteDiagnosis(@SwaggerRequestBody(description = "Request to delete a diagnosis") @RequestBody diagnosisRequest: DiagnosisRequest) : ResponseEntity<String> {
        log.info("Deleting diagnosis $diagnosisRequest")

        if (diagnosisRequest.diagnosisName.isEmpty()) {
            return ResponseEntity.badRequest().body("Diagnosis name cannot be empty")
        }

        if(!diagnosisService.deleteDiagnosis(diagnosisRequest.diagnosisName)) {
            return ResponseEntity.badRequest().body("Diagnosis does not exist")
        }
        replConfig.regenerateSingleModel().invoke("diagnosis")

        // Remove the object from the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/diagnosis_${diagnosisRequest.diagnosisName}
            :diagnosis_${diagnosisRequest.diagnosisName} rdf:type owl:NamedIndividual ,
                            :Diagnosis ;
                :diagnosisName "${diagnosisRequest.diagnosisName}" .
            """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, "")

        return ResponseEntity.ok("Diagnosis deleted")
    }
}