package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Task
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
    private val replConfig: REPLConfig,
    private val triplestoreService: TriplestoreService
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
        if(!triplestoreService.createTask(task.taskName, task.averageDuration, task.bed)) {
            return ResponseEntity.badRequest().body("Error: the task could not be created.")
        }
        replConfig.regenerateSingleModel().invoke("tasks")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = """
            $fileContent
            
            ###  http://$ttlPrefix/diagnosis_${task.taskName}
            :task_${task.taskName} rdf:type owl:NamedIndividual ,
                            :Task ;
                :taskName "${task.taskName}" ;
                :averageDuration "${task.averageDuration}"^^xsd:double ;
                :bed "${task.bed}"^^xsd:integer .
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
        val taskList = triplestoreService.getAllTasks() ?: return ResponseEntity.badRequest().body(listOf("No tasks found"))
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

        if(!triplestoreService.updateTask(updateTaskRequest.oldTaskName, updateTaskRequest.oldAverageDuration, updateTaskRequest.oldBed, updateTaskRequest.newTaskName, updateTaskRequest.newAverageDuration,  updateTaskRequest.newBed)) {
            return ResponseEntity.badRequest().body("Error: the task could not be updated.")
        }
        replConfig.regenerateSingleModel().invoke("tasks")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = fileContent.replace(
            """
            ###  http://$ttlPrefix/task_${updateTaskRequest.oldTaskName}
           :task_${updateTaskRequest.oldTaskName} rdf:type owl:NamedIndividual ,
                            :Task ;
                :taskName "${updateTaskRequest.oldTaskName}" ;
                :averageDuration "${updateTaskRequest.oldAverageDuration}"^^xsd:double ;
                :bed "${updateTaskRequest.oldBed}"^^xsd:integer .
        """.trimIndent(),
            """
            ###  http://$ttlPrefix/task_${updateTaskRequest.newTaskName}
            :task_${updateTaskRequest.newTaskName} rdf:type owl:NamedIndividual ,
                             :Task ;
                 :taskName "${updateTaskRequest.newTaskName}" ;
                 :averageDuration "${updateTaskRequest.newAverageDuration}"^^xsd:double ;
                 :bed "${updateTaskRequest.newBed}"^^xsd:integer .
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

        if(!triplestoreService.deleteTask(taskRequest.taskName, taskRequest.averageDuration, taskRequest.bed)) {
            return ResponseEntity.badRequest().body("Error: the task could not be deleted.")
        }
        replConfig.regenerateSingleModel().invoke("tasks")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = fileContent.replace(
            """
            ###  http://$ttlPrefix/task_${taskRequest.taskName}
            :task_${taskRequest.taskName} rdf:type owl:NamedIndividual ,
                            :Task ;
                :taskName "${taskRequest.taskName}" ;
                :averageDuration "${taskRequest.averageDuration}"^^xsd:double ;
                :bed "${taskRequest.bed}"^^xsd:integer .
        """.trimIndent(),
            ""
        )

        File(path).writeText(newContent)

        return ResponseEntity.ok("Task deleted")
    }
}