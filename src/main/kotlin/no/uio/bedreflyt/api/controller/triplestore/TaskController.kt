package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.annotations.ApiParam
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
import org.springframework.web.bind.annotation.PathVariable

@RestController
@RequestMapping("/api/v1/fuseki/tasks")
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
    @PostMapping(produces= ["application/json"])
    fun createTask(@SwaggerRequestBody(description = "Request to add a new task") @RequestBody taskRequest: TaskRequest) : ResponseEntity<Task> {
        log.info("Creating task $taskRequest")

        if(!taskService.createTask(taskRequest.taskName)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("tasks")

        return ResponseEntity.ok(Task(taskRequest.taskName))
    }

    @Operation(summary = "Retrieve all tasks")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Successfully retrieved the tasks"),
        ApiResponse(responseCode = "401", description = "You are not authorized to view the resource"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "404", description = "The resource you were trying to reach is not found")
    ])
    @GetMapping(produces= ["application/json"])
    fun retrieveTasks() : ResponseEntity<List<Task>> {
        log.info("Retrieving tasks")

        val tasks = taskService.getAllTasks() ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(tasks)
    }

    @Operation(summary = "Retrieve a task")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Task found"),
        ApiResponse(responseCode = "400", description = "Invalid task"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "404", description = "Task not found")
    ])
    @GetMapping("/{taskName}", produces= ["application/json"])
    fun retrieveTask(@ApiParam(value = "Task name", required = true) @PathVariable taskName: String) : ResponseEntity<Task> {
        log.info("Retrieving task $taskName")

        val task = taskService.getTaskByTaskName(taskName) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(task)
    }

    @Operation(summary = "Update a task")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Task updated"),
        ApiResponse(responseCode = "400", description = "Invalid task"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PatchMapping("/{taskName}", produces= ["application/json"])
    fun updateTask(@ApiParam(value = "Task name", required = true) @PathVariable taskName: String,
                   @SwaggerRequestBody(description = "Request to update a task") @RequestBody updateTaskRequest: UpdateTaskRequest) : ResponseEntity<Task> {
        log.info("Updating task $updateTaskRequest")

        val task = taskService.getTaskByTaskName(taskName) ?: return ResponseEntity.notFound().build()
        updateTaskRequest.newTaskName?.let {
            if(!taskService.updateTask(task, it)) {
                return ResponseEntity.badRequest().build()
            }
        } ?: return ResponseEntity.noContent().build()

        replConfig.regenerateSingleModel().invoke("tasks")

        return ResponseEntity.ok(Task(updateTaskRequest.newTaskName))
    }

    @Operation(summary = "Delete a task")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Task deleted"),
        ApiResponse(responseCode = "400", description = "Invalid task"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @DeleteMapping("/{taskName}", produces= ["application/json"])
    fun deleteTask(@ApiParam(value = "Task name", required = true) @PathVariable taskName: String) : ResponseEntity<String> {
        log.info("Deleting task $taskName")

        val task = taskService.getTaskByTaskName(taskName) ?: return ResponseEntity.notFound().build()
        if(!taskService.deleteTask(task)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("tasks")

        return ResponseEntity.ok("Task $taskName deleted")
    }
}