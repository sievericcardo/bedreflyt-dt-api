package no.uio.bedreflyt.api.controller.triplestore

import io.swagger.annotations.ApiParam
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.model.triplestore.Task
import no.uio.bedreflyt.api.service.triplestore.TaskService
import no.uio.bedreflyt.api.types.TaskRequest
import no.uio.bedreflyt.api.types.UpdateTaskRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RequestBody
import java.util.logging.Logger
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/fuseki/tasks")
class TaskController (
    private val replConfig: REPLConfig,
    private val taskService: TaskService
) {

    private val log : Logger = Logger.getLogger(TaskController::class.java.name)

    @Operation(summary = "Create a new task")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Task created"),
        ApiResponse(responseCode = "400", description = "Invalid task"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping(produces= ["application/json"])
    fun createTask(@SwaggerRequestBody(description = "Request to add a new task") @Valid @RequestBody taskRequest: TaskRequest) : ResponseEntity<Task> {
        log.info("Creating task $taskRequest")

        val newTask = taskService.createTask(taskRequest.taskName) ?: return ResponseEntity.badRequest().build()
        replConfig.regenerateSingleModel().invoke("tasks")

        return ResponseEntity.ok(newTask)
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
    fun retrieveTask(@ApiParam(value = "Task name", required = true) @Valid @PathVariable taskName: String) : ResponseEntity<Task> {
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
    fun updateTask(@ApiParam(value = "Task name", required = true) @Valid @PathVariable taskName: String,
                   @SwaggerRequestBody(description = "Request to update a task") @Valid @RequestBody updateTaskRequest: UpdateTaskRequest) : ResponseEntity<Task> {
        log.info("Updating task $updateTaskRequest")

        val task = taskService.getTaskByTaskName(taskName) ?: return ResponseEntity.notFound().build()
        val updatedTask = updateTaskRequest.newTaskName?.let {
            taskService.updateTask(task, it) ?: return ResponseEntity.badRequest().build()
        } ?: return ResponseEntity.noContent().build()

        replConfig.regenerateSingleModel().invoke("tasks")

        return ResponseEntity.ok(updatedTask)
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
    fun deleteTask(@ApiParam(value = "Task name", required = true) @Valid @PathVariable taskName: String) : ResponseEntity<String> {
        log.info("Deleting task $taskName")

        val task = taskService.getTaskByTaskName(taskName) ?: return ResponseEntity.notFound().build()
        if(!taskService.deleteTask(task)) {
            return ResponseEntity.badRequest().build()
        }
        replConfig.regenerateSingleModel().invoke("tasks")

        return ResponseEntity.ok("Task $taskName deleted")
    }
}