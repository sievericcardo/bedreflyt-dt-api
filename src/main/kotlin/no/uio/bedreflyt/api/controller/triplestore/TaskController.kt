package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Task
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

data class TaskRequest (
    val taskName : String,
    val averageDuration: Double,
    val bed: Int
)

data class UpdateTaskRequest (
    val oldTaskName: String,
    val newTaskName: String,
    val oldAverageDuration: Double,
    val newAverageDuration: Double,
    val oldBed: Int,
    val newBed: Int
)

@RestController
@RequestMapping("/api/fuseki/task")
class TaskController (
    private val replConfig: REPLConfig
) {

    private val log : Logger = Logger.getLogger(TaskController::class.java.name)
    private val host = System.getenv().getOrDefault("TRIPLESTORE_URL", "localhost")
    private val dataStore = System.getenv().getOrDefault("TRIPLESTORE_DATASET", "Bedreflyt")
    private val tripleStore = "http://$host:3030/$dataStore"
    private val prefix = System.getenv().getOrDefault("DOMAIN_PREFIX", "http://www.smolang.org/bedreflyt#")
    private val ttlPrefix = if (prefix.isNotEmpty()) prefix.dropLast(1) else prefix
    private val repl = replConfig.repl()

    @Operation(summary = "Create a new task")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Task created"),
        ApiResponse(responseCode = "400", description = "Invalid task"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/create")
    fun createTask(@SwaggerRequestBody(description = "Request to add a new task") @RequestBody taskRequest: TaskRequest) : ResponseEntity<String> {
        log.info("Creating task $taskRequest")

        val task = Task(taskRequest.taskName, taskRequest.averageDuration, taskRequest.bed)
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :task_${task.taskName} a :Task ;
                    :taskName "${task.taskName}" ;
                    :averageDuration ${task.averageDuration} ;
                    :bed ${task.bed} .
            }
        """

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
            
            ###  http://$ttlPrefix/diagnosis_${task.taskName}
            :task_${task.taskName} rdf:type owl:NamedIndividual ,
                            :Task ;
                :taskName "${task.taskName}" ;
                :averageDuration ${task.averageDuration} ;
                :bed ${task.bed} .
        """.trimIndent()

        File(path).writeText(newContent)

        return ResponseEntity.ok("Task created")
    }

    @Operation(summary = "Retrieve all tasks")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Successfully retrieved the tasks"),
        ApiResponse(responseCode = "401", description = "You are not authorized to view the resource"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "404", description = "The resource you were trying to reach is not found")
    ])
    @GetMapping("/retrieve")
    fun retrieveTasks() : ResponseEntity<List<Any>> {
        log.info("Retrieving tasks")

        val taskList = mutableListOf<Any>()

        val diagnosis = """
            SELECT * WHERE {
                ?obj a prog:Task ;
                    prog:Task_taskName ?name ;
                    prog:Task_averageDuration ?averageDuration ;
                    prog:Task_bed ?bed .
            }"""

        val resultDiagnosis: ResultSet = repl.interpreter!!.query(diagnosis)!!

        if (!resultDiagnosis.hasNext()) {
            return ResponseEntity.badRequest().body(listOf("No diagnosis found"))
        }
        while (resultDiagnosis.hasNext()) {
            val solution: QuerySolution = resultDiagnosis.next()
            val name = solution.get("?name").asLiteral().toString()
            val averageDuration = solution.get("?averageDuration").asLiteral().toString().split("^^")[0].toDouble()
            val bed = solution.get("?bed").asLiteral().toString().split("^^")[0].toInt()
            taskList.add(Task(name, averageDuration, bed))
        }

        return ResponseEntity.ok(taskList)
    }

    @Operation(summary = "Update a task")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Task updated"),
        ApiResponse(responseCode = "400", description = "Invalid task"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/update")
    fun updateTask(@SwaggerRequestBody(description = "Request to update a task") @RequestBody updateTaskRequest: UpdateTaskRequest) : ResponseEntity<String> {
        log.info("Updating task $updateTaskRequest")

        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :task_${updateTaskRequest.oldTaskName} :taskName "${updateTaskRequest.oldTaskName}" ;
                    :averageDuration ${updateTaskRequest.oldAverageDuration} ;
                    :bed ${updateTaskRequest.oldBed} .
            }
            INSERT {
                :task_${updateTaskRequest.newTaskName} a :Task ;
                    :taskName "${updateTaskRequest.newTaskName}" ;
                    :averageDuration ${updateTaskRequest.newAverageDuration} ;
                    :bed ${updateTaskRequest.newBed} .
            }
            WHERE {
                :task_${updateTaskRequest.oldTaskName} :taskName "${updateTaskRequest.oldTaskName}" ;
                    :averageDuration ${updateTaskRequest.oldAverageDuration} ;
                    :bed ${updateTaskRequest.oldBed} .
            }
        """

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
            ###  http://$ttlPrefix/task_${updateTaskRequest.oldTaskName}
           :task_${updateTaskRequest.oldTaskName} rdf:type owl:NamedIndividual ,
                            :Task ;
                :taskName "${updateTaskRequest.oldTaskName}" ;
                :averageDuration ${updateTaskRequest.oldAverageDuration} ;
                :bed ${updateTaskRequest.oldBed} .
        """.trimIndent(),
            """
            ###  http://$ttlPrefix/task_${updateTaskRequest.newTaskName}
            :task_${updateTaskRequest.newTaskName} rdf:type owl:NamedIndividual ,
                             :Task ;
                 :taskName "${updateTaskRequest.newTaskName}" ;
                 :averageDuration ${updateTaskRequest.newAverageDuration} ;
                 :bed ${updateTaskRequest.newBed} .
        """.trimIndent()
        )

        File(path).writeText(newContent)

        return ResponseEntity.ok("Task updated")
    }

    @Operation(summary = "Delete a task")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Task deleted"),
        ApiResponse(responseCode = "400", description = "Invalid task"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/delete")
    fun deleteTask(@SwaggerRequestBody(description = "Request to delete a task") @RequestBody taskRequest: TaskRequest) : ResponseEntity<String> {
        log.info("Deleting task $taskRequest")

        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :task_${taskRequest.taskName} a :Task ;
                    :taskName "${taskRequest.taskName}" ;
                    :averageDuration ${taskRequest.averageDuration} ;
                    :bed ${taskRequest.bed} .
            }
            WHERE {
                :task_${taskRequest.taskName} a :Task ;
                    :taskName "${taskRequest.taskName}" ;
                    :averageDuration ${taskRequest.averageDuration} ;
                    :bed ${taskRequest.bed} .
            }
        """

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
            ###  http://$ttlPrefix/task_${taskRequest.taskName}
            :task_${taskRequest.taskName} rdf:type owl:NamedIndividual ,
                            :Task ;
                :taskName "${taskRequest.taskName}" ;
                :averageDuration ${taskRequest.averageDuration} ;
                :bed ${taskRequest.bed} .
        """.trimIndent(),
            ""
        )

        File(path).writeText(newContent)

        return ResponseEntity.ok("Task deleted")
    }
}