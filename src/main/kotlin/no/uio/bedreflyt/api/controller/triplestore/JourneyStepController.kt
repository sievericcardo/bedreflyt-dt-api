package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.JourneyStep
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.apache.jena.update.UpdateRequest
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
    private val replConfig: REPLConfig
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

        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :journeyStep${journeyStep.journeyOrder}_${journeyStep.diagnosis} a :JourneyStep ;
                    :diagnosis "${journeyStep.diagnosis}" ;
                    :journeyOrder ${journeyStep.journeyOrder} ;
                    :task "${journeyStep.task}" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body("Error: the update query could not be executed.")
        }

        repl.interpreter!!.tripleManager.regenerateTripleStoreModel()
        repl.interpreter!!.evalCall(
            repl.interpreter!!.getObjectNames("AssetModel")[0],
            "AssetModel",
            "reconfigure")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = """
            $fileContent
            
            ###  http://$ttlPrefix/journeyStep${journeyStep.journeyOrder}_${journeyStep.diagnosis}
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
    fun retrieveJourneySteps() : ResponseEntity<List<Any>> {
        log.info("Retrieving journey steps")

        val query = """
            SELECT ?diagnosis ?journeyOrder ?task
            WHERE {
                ?obj a prog:JourneyStep ;
                    prog:JourneyStep_diagnosis ?diagnosis ;
                    prog:JourneyStep_journeyOrder ?journeyOrder ;
                    prog:JourneyStep_task ?task .
            }
        """.trimIndent()

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!

        val journeySteps = mutableListOf<JourneyStep>()

        if (!resultSet.hasNext()) {
            return ResponseEntity.badRequest().body(listOf("No diagnosis found"))
        }
        while (resultSet.hasNext()) {
            val solution: QuerySolution = resultSet.next()
            val diagnosis = solution.get("?diagnosis").asLiteral().toString()
            val journeyOrder = solution.get("?journeyOrder").asLiteral().toString().split("^^")[0].toInt()
            val task = solution.get("?task").asLiteral().toString()
            journeySteps.add(JourneyStep(diagnosis, journeyOrder, task))
        }

        return ResponseEntity.ok(journeySteps)
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
        log.info("Updating journey step")

        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :journeyStep${journeyStep.oldJourneyOrder}_${journeyStep.oldDiagnosis} :diagnosis "${journeyStep.oldDiagnosis}" ;
                    :journeyOrder ${journeyStep.oldJourneyOrder} ;
                    :task "${journeyStep.oldTask}" .
            }
            INSERT {
                :journeyStep${journeyStep.newJourneyOrder}_${journeyStep.newDiagnosis} a :JourneyStep ;
                    :diagnosis "${journeyStep.newDiagnosis}" ;
                    :journeyOrder ${journeyStep.newJourneyOrder} ;
                    :task "${journeyStep.newTask}" .
            }
            WHERE {
                :journeyStep${journeyStep.oldJourneyOrder}_${journeyStep.oldDiagnosis} a :JourneyStep ;
                    :diagnosis "${journeyStep.oldDiagnosis}" ;
                    :journeyOrder ${journeyStep.oldJourneyOrder} ;
                    :task "${journeyStep.oldTask}" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body("Error: the update query could not be executed.")
        }

        repl.interpreter!!.tripleManager.regenerateTripleStoreModel()
        repl.interpreter!!.evalCall(
            repl.interpreter!!.getObjectNames("AssetModel")[0],
            "AssetModel",
            "reconfigure"
        )

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = fileContent.replace(
            """
            ###  http://$ttlPrefix/journeyStep${journeyStep.oldJourneyOrder}_${journeyStep.oldDiagnosis}
            :journeyStep${journeyStep.oldJourneyOrder}_${journeyStep.oldDiagnosis} rdf:type owl:NamedIndividual ,
                            :JourneyStep ;
                :diagnosis "${journeyStep.oldDiagnosis}" ;
                :journeyOrder ${journeyStep.oldJourneyOrder} ;
                :task "${journeyStep.oldTask}" .
            """.trimIndent(),
            """
            ###  http://$ttlPrefix/journeyStep${journeyStep.oldJourneyOrder}_${journeyStep.oldDiagnosis}
            :journeyStep${journeyStep.oldJourneyOrder}_${journeyStep.oldDiagnosis} rdf:type owl:NamedIndividual ,
                            :JourneyStep ;
                :diagnosis "${journeyStep.newDiagnosis}" ;
                :journeyOrder ${journeyStep.newJourneyOrder} ;
                :task "${journeyStep.newTask}" .
            """.trimIndent()
        )

        File(path).writeText(newContent)

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

        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :journeyStep${journeyStep.journeyOrder}_${journeyStep.diagnosis} a :JourneyStep ;
                    :diagnosis "${journeyStep.diagnosis}" ;
                    :journeyOrder ${journeyStep.journeyOrder} ;
                    :task "${journeyStep.task}" .
            }
            WHERE {
                :journeyStep${journeyStep.journeyOrder}_${journeyStep.diagnosis} a :JourneyStep ;
                    :diagnosis "${journeyStep.diagnosis}" ;
                    :journeyOrder ${journeyStep.journeyOrder} ;
                    :task "${journeyStep.task}" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body("Error: the update query could not be executed.")
        }

        repl.interpreter!!.tripleManager.regenerateTripleStoreModel()
        repl.interpreter!!.evalCall(
            repl.interpreter!!.getObjectNames("AssetModel")[0],
            "AssetModel",
            "reconfigure"
        )

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = fileContent.replace(
            """
            ###  http://$ttlPrefix/journeyStep${journeyStep.journeyOrder}_${journeyStep.diagnosis}
            :journeyStep${journeyStep.journeyOrder}_${journeyStep.diagnosis} rdf:type owl:NamedIndividual ,
                            :JourneyStep ;
                :diagnosis "${journeyStep.diagnosis}" ;
                :journeyOrder ${journeyStep.journeyOrder} ;
                :task "${journeyStep.task}" .
            """.trimIndent(),
            ""
        )

        File(path).writeText(newContent)

        return ResponseEntity.ok("Journey step deleted")
    }
}