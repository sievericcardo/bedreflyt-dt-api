package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.JourneyStep
import no.uio.bedreflyt.api.service.triplestore.TriplestoreService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.util.logging.Logger

data class JourneyStepRequest (
    val diagnosis : String,
    val journeyOrder: Int,
    val task: String
)

data class UpdateJourneyStepRequest (
    val oldDiagnosis: String,
    val oldJourneyOrder: Int,
    val oldTask: String,
    val newDiagnosis: String,
    val newJourneyOrder: Int,
    val newTask: String
)

@RestController
@RequestMapping("/api/fuseki/journey-step")
class JourneyStepController (
    private val replConfig: REPLConfig,
    private val triplestoreService: TriplestoreService
) {

    private val log : Logger = Logger.getLogger(JourneyStepController::class.java.name)
    private val host = System.getenv().getOrDefault("TRIPLESTORE_URL", "localhost")
    private val dataStore = System.getenv().getOrDefault("TRIPLESTORE_DATASET", "Bedreflyt")
    private val tripleStore = "http://$host:3030/$dataStore"
    private val prefix = System.getenv().getOrDefault("DOMAIN_PREFIX", "http://www.smolang.org/bedreflyt#")
    private val ttlPrefix = if (prefix.isNotEmpty()) prefix.dropLast(1) else prefix
    private val repl = replConfig.repl()

    @Operation(summary = "Add a journey step")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Journey step added"),
        ApiResponse(responseCode = "400", description = "Invalid journey step"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/create")
    fun addJourneyStep(@SwaggerRequestBody(description = "Journey step to add") @RequestBody journeyStep: JourneyStepRequest) : ResponseEntity<String> {
        log.info("Adding journey step")

        if(!triplestoreService.createTreatment(journeyStep.diagnosis, journeyStep.journeyOrder, journeyStep.task)) {
            return ResponseEntity.badRequest().body("Error: the diagnosis does not exist.")
        }
        replConfig.regenerateSingleModel().invoke("journey steps")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = """
            $fileContent
            
            ###  $ttlPrefix/journeyStep${journeyStep.journeyOrder}_${journeyStep.diagnosis}
            :journeyStep${journeyStep.journeyOrder}_${journeyStep.diagnosis} rdf:type owl:NamedIndividual ,
                            :JourneyStep ;
                :diagnosis "${journeyStep.diagnosis}" ;
                :journeyOrder ${journeyStep.journeyOrder} ;
                :task "${journeyStep.task}" .
        """.trimIndent()

        File(path).writeText(newContent)

        return ResponseEntity.ok("Journey step added")
    }

    @Operation(summary = "Retrieve all journey steps")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Journey steps retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid journey steps"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/retrieve")
    fun retrieveJourneySteps() : ResponseEntity<Map<String, List<Any>>> {
        log.info("Retrieving journey steps")
        val journeySteps = triplestoreService.getAllTreatments() ?: return ResponseEntity.badRequest().body(mapOf("error" to listOf("No journey steps found")))

        val journeyStepsDict = mutableMapOf<String, MutableList<JourneyStep>>()
        journeySteps.forEach { journeyStep ->
            if (journeyStepsDict.containsKey(journeyStep.diagnosis)) {
                journeyStepsDict[journeyStep.diagnosis]!!.add(journeyStep)
            } else {
                journeyStepsDict[journeyStep.diagnosis] = mutableListOf(journeyStep)
            }
        }

        journeyStepsDict.forEach { (_, journeySteps) ->
            journeySteps.sortBy { it.orderInJourney }
        }

        return ResponseEntity.ok(journeyStepsDict)
    }

    @Operation(summary = "Update a journey step")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Journey step updated"),
        ApiResponse(responseCode = "400", description = "Invalid journey step"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/update")
    fun updateJourneyStep(@SwaggerRequestBody(description = "Journey step to update") @RequestBody journeyStep: UpdateJourneyStepRequest) : ResponseEntity<String> {
        return ResponseEntity.internalServerError().body("Not implemented")
        log.info("Updating journey step")

        if(!triplestoreService.updateTreatment(journeyStep.oldDiagnosis, journeyStep.oldJourneyOrder, journeyStep.oldTask, journeyStep.newDiagnosis, journeyStep.newJourneyOrder, journeyStep.newTask)) {
            return ResponseEntity.badRequest().body("Error: the journey step could not be updated.")
        }
        replConfig.regenerateSingleModel().invoke("journey steps")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/journeyStep${journeyStep.oldJourneyOrder}_${journeyStep.oldDiagnosis}
            :journeyStep${journeyStep.oldJourneyOrder}_${journeyStep.oldDiagnosis} rdf:type owl:NamedIndividual ,
                            :JourneyStep ;
                :diagnosis "${journeyStep.oldDiagnosis}" ;
                :journeyOrder ${journeyStep.oldJourneyOrder} ;
                :task "${journeyStep.oldTask}" .
            """.trimIndent()
        val newContent = """
            ###  $ttlPrefix/journeyStep${journeyStep.newJourneyOrder}_${journeyStep.newDiagnosis}
            :journeyStep${journeyStep.newJourneyOrder}_${journeyStep.newDiagnosis} rdf:type owl:NamedIndividual ,
                            :JourneyStep ;
                :diagnosis "${journeyStep.newDiagnosis}" ;
                :journeyOrder ${journeyStep.newJourneyOrder} ;
                :task "${journeyStep.newTask}" .
            """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, newContent)

        return ResponseEntity.ok("Journey step updated")
    }

    @Operation(summary = "Delete a journey step")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Journey step deleted"),
        ApiResponse(responseCode = "400", description = "Invalid journey step"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/delete")
    fun deleteJourneyStep(@SwaggerRequestBody(description = "Journey step to delete") @RequestBody journeyStep: JourneyStepRequest) : ResponseEntity<String> {
        log.info("Deleting journey step")

        if(!triplestoreService.deleteTreatment(journeyStep.diagnosis, journeyStep.journeyOrder, journeyStep.task)) {
            return ResponseEntity.badRequest().body("Error: the journey step could not be deleted.")
        }
        replConfig.regenerateSingleModel().invoke("journey steps")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/journeyStep${journeyStep.journeyOrder}_${journeyStep.diagnosis}
            :journeyStep${journeyStep.journeyOrder}_${journeyStep.diagnosis} rdf:type owl:NamedIndividual ,
                            :JourneyStep ;
                :diagnosis "${journeyStep.diagnosis}" ;
                :journeyOrder ${journeyStep.journeyOrder} ;
                :task "${journeyStep.task}" .
            """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, "")

        return ResponseEntity.ok("Journey step deleted")
    }
}