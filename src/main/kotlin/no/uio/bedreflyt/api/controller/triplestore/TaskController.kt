package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.EnvironmentConfig
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Task
import no.uio.bedreflyt.api.service.triplestore.TaskService
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
import no.uio.bedreflyt.api.types.TaskRequest
import no.uio.bedreflyt.api.types.UpdateTaskRequest
import no.uio.bedreflyt.api.types.DeleteTaskRequest

@RestController
@RequestMapping("/api/fuseki/task")
class TaskController (
    private val replConfig: REPLConfig,
    private val environmentConfig: EnvironmentConfig,
    private val triplestoreService: TriplestoreService,
    private val taskService: TaskService
) {

    private val log : Logger = Logger.getLogger(TaskController::class.java.name)
    private val host = environmentConfig.getOrDefault("TRIPLESTORE_URL", "localhost")
    private val dataStore = environmentConfig.getOrDefault("TRIPLESTORE_DATASET", "Bedreflyt")
    private val tripleStore = "http://$host:3030/$dataStore"
    private val prefix = environmentConfig.getOrDefault("DOMAIN_PREFIX", "http://www.smolang.org/bedreflyt#")
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
        if(!taskService.createTask(task.taskName, task.averageDuration, task.bed)) {
            return ResponseEntity.badRequest().body("Error: the task could not be created.")
        }
        replConfig.regenerateSingleModel().invoke("tasks")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val fileContent = File(path).readText(Charsets.UTF_8)
        val newContent = """
            $fileContent
            
            ###  $ttlPrefix/task_${task.taskName}
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
    fun retrieveTasks() : ResponseEntity<List<Task>> {
        log.info("Retrieving tasks")
        val taskList = taskService.getAllTasks() ?: return ResponseEntity.noContent().build()
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
    @PatchMapping("/update")
    fun updateTask(@SwaggerRequestBody(description = "Request to update a task") @RequestBody updateTaskRequest: UpdateTaskRequest) : ResponseEntity<String> {
        log.info("Updating task $updateTaskRequest")

        val task = taskService.getTaskByTaskName(updateTaskRequest.taskName) ?: return ResponseEntity.badRequest().body("Error: the task could not be found.")
        val newAverageDuration = updateTaskRequest.newAverageDuration ?: task.averageDuration
        val newBed = updateTaskRequest.newBed ?: task.bed

        if(!taskService.updateTask(task, newAverageDuration, newBed)) {
            return ResponseEntity.badRequest().body("Error: the task could not be updated.")
        }
        replConfig.regenerateSingleModel().invoke("tasks")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/task_${task.taskName}
           :task_${task.taskName} rdf:type owl:NamedIndividual ,
                            :Task ;
                :taskName "${task.taskName}" ;
                :averageDuration "${task.averageDuration}"^^xsd:double ;
                :bed "${task.bed}"^^xsd:integer .
        """.trimIndent()
        val newContent = """
            ###  $ttlPrefix/task_${task.taskName}
            :task_${task.taskName} rdf:type owl:NamedIndividual ,
                             :Task ;
                 :taskName "${task.taskName}" ;
                 :averageDuration "${updateTaskRequest.newAverageDuration}"^^xsd:double ;
                 :bed "${updateTaskRequest.newBed}"^^xsd:integer .
        """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, newContent)

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
    @DeleteMapping("/delete")
    fun deleteTask(@SwaggerRequestBody(description = "Request to delete a task") @RequestBody taskRequest: DeleteTaskRequest) : ResponseEntity<String> {
        log.info("Deleting task $taskRequest")

        val task = taskService.getTaskByTaskName(taskRequest.taskName) ?: return ResponseEntity.badRequest().body("Error: the task could not be found.")

        if(!taskService.deleteTask(task)) {
            return ResponseEntity.badRequest().body("Error: the task could not be deleted.")
        }
        replConfig.regenerateSingleModel().invoke("tasks")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/task_${task.taskName}
            :task_${task.taskName} rdf:type owl:NamedIndividual ,
                            :Task ;
                :taskName "${task.taskName}" ;
                :averageDuration "${task.averageDuration}"^^xsd:double ;
                :bed "${task.bed}"^^xsd:integer .
        """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, "")

        return ResponseEntity.ok("Task deleted")
    }
}