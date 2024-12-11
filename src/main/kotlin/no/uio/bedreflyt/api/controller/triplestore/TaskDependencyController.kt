package no.uio.bedreflyt.api.controller.triplestore

import no.uio.bedreflyt.api.model.triplestore.Task
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.TaskDependency
import no.uio.bedreflyt.api.service.triplestore.DiagnosisService
import no.uio.bedreflyt.api.service.triplestore.TaskDependencyService
import no.uio.bedreflyt.api.service.triplestore.TaskService
import no.uio.bedreflyt.api.service.triplestore.TriplestoreService
import org.springframework.http.ResponseEntity
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.util.logging.Logger

sealed class TaskOrString {
    data class TaskType(val task: Task) : TaskOrString()
    data class StringType(val value: String) : TaskOrString()
}

data class TreatmentRequest (
    val diagnosis: String,
    val task: TaskOrString,
    val dependsOn: String
)

data class UpdateTaskDependencyRequest (
    val diagnosis: String,
    val taskName: String,
    val oldDependsOn: String,
    val newDependsOn: String
)

data class DeleteTaskDependencyRequest (
    val diagnosis: String,
    val taskName: String
)

@RestController
@RequestMapping("/api/fuseki/task-dependency")
class TaskDependencyController (
    private val replConfig: REPLConfig,
    private val triplestoreProperties: TriplestoreProperties,
    private val triplestoreService: TriplestoreService,
    private val taskService: TaskService,
    private val diagnosisService: DiagnosisService,
    private val taskDependencyService: TaskDependencyService
) {

    private val log : Logger = Logger.getLogger(TaskDependencyController::class.java.name)
    private val ttlPrefix = triplestoreProperties.ttlPrefix

    @Operation(summary = "Create a new treatment from task dependencies")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Treatment created"),
        ApiResponse(responseCode = "400", description = "Invalid treatment"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/create")
    fun createTreatment(@SwaggerRequestBody(description = "Request to add a new treatment") @RequestBody treatmentRequest: List<TreatmentRequest>) : ResponseEntity<String> {
        log.info("Creating treatment $treatmentRequest")
        var taskName = ""

        for (treatment in treatmentRequest) {
            if (diagnosisService.createDiagnosis(treatment.diagnosis)) {
                log.info("New diagnosis created: ${treatment.diagnosis}")
            }
            if (treatment.task is TaskOrString.TaskType) {
                val taskObj = treatment.task as TaskOrString.TaskType
                if(taskService.createTask(taskObj.task.taskName, taskObj.task.averageDuration, taskObj.task.bed)) {
                    log.info("Created new task: ${taskObj.task.taskName}")
                }

                taskName = taskObj.task.taskName
            } else if (treatment.task is TaskOrString.StringType) {
                val task = treatment.task as TaskOrString.StringType
                if(taskService.getTaskByTaskName(task.value) == null) {
                    return ResponseEntity.badRequest().body("Task does not exist")
                }

                taskName = task.value
            }
            if(!taskDependencyService.createTaskDependency(treatment.diagnosis, taskName, treatment.dependsOn)) {
                return ResponseEntity.badRequest().body("Task dependency already exists")
            }

            // Append to the file bedreflyt.ttl
            val path = "bedreflyt.ttl"
            val fileContent = File(path).readText(Charsets.UTF_8)
            val newContent = """
            ###  $ttlPrefix/taskDependency_$taskName
            :taskDependency_$taskName rdf:type owl:NamedIndividual ,
                            :taskDependency ;
                :diagnosisName "${treatment.diagnosis}" ;
                :taskDependent "$taskName" ;
                :taskToWait "${treatment.dependsOn}" .
            """.trimIndent()

            File(path).writeText(newContent)
        }

        replConfig.regenerateSingleModel().invoke("task dependencies")

        return ResponseEntity.ok("Treatment created")
    }

    @Operation(summary = "Get all task dependencies")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Task dependencies found"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/retrieve")
    fun getTaskDependencies() : ResponseEntity<Map<String, List<Any>>> {
        log.info("Getting task dependencies")
        val taskDependencies = taskDependencyService.getAllTaskDependencies() ?: return ResponseEntity.badRequest().body(mapOf("error" to listOf("No task dependencies steps found")))

        val taskDependenciesDict = mutableMapOf<String, MutableList<TaskDependency>>()
        taskDependencies.forEach { taskDependency ->
            if (taskDependenciesDict.containsKey(taskDependency.diagnosis)) {
                taskDependenciesDict[taskDependency.diagnosis]!!.add(taskDependency)
            } else {
                taskDependenciesDict[taskDependency.diagnosis] = mutableListOf(taskDependency)
            }
        }

        return ResponseEntity.ok(taskDependenciesDict)
    }

    @Operation(summary = "Update a task dependency")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Task dependency updated"),
        ApiResponse(responseCode = "400", description = "Invalid task dependency"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/update")
    fun updateTaskDependency(@SwaggerRequestBody(description = "Request to update a task dependency") @RequestBody updateTreatmentRequest: UpdateTaskDependencyRequest) : ResponseEntity<String> {
        log.info("Updating task dependency $updateTreatmentRequest")

        if (!taskDependencyService.updateTaskDependency(updateTreatmentRequest.diagnosis, updateTreatmentRequest.taskName, updateTreatmentRequest.oldDependsOn, updateTreatmentRequest.newDependsOn)) {
            return ResponseEntity.badRequest().body("Task dependency does not exist")
        }

        replConfig.regenerateSingleModel().invoke("task dependencies")

        // Update the object in the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/taskDependency_${updateTreatmentRequest.taskName}
            :taskDependency_${updateTreatmentRequest.taskName} rdf:type owl:NamedIndividual ,
                            :taskDependency ;
                :diagnosisName "${updateTreatmentRequest.diagnosis}" ;
                :taskDependent "${updateTreatmentRequest.taskName}" ;
                :taskToWait "${updateTreatmentRequest.oldDependsOn}" .
            """.trimIndent()
        val newContent = """
            ###  $ttlPrefix/taskDependency_${updateTreatmentRequest.taskName}
            :taskDependency_${updateTreatmentRequest.taskName} rdf:type owl:NamedIndividual ,
                            :taskDependency ;
                :diagnosisName "${updateTreatmentRequest.diagnosis}" ;
                :taskDependent "${updateTreatmentRequest.taskName}" ;
                :taskToWait "${updateTreatmentRequest.newDependsOn}" .
            """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, newContent)

        return ResponseEntity.ok("Task dependency updated")
    }

    @Operation(summary = "Delete a task dependency")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Task dependency deleted"),
        ApiResponse(responseCode = "400", description = "Invalid task dependency"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @PostMapping("/delete")
    fun deleteTaskDependency(@SwaggerRequestBody(description = "Request to delete a task dependency") @RequestBody taskRequest: DeleteTaskDependencyRequest) : ResponseEntity<String> {
        log.info("Deleting task dependency $taskRequest")

        if (!taskDependencyService.deleteTaskDependency(taskRequest.diagnosis, taskRequest.taskName)) {
            return ResponseEntity.badRequest().body("Task dependency does not exist")
        }

        replConfig.regenerateSingleModel().invoke("task dependencies")

        // Append to the file bedreflyt.ttl
        val path = "bedreflyt.ttl"
        val oldContent = """
            ###  $ttlPrefix/taskDependency_${taskRequest.taskName}
            :taskDependency_${taskRequest.taskName} rdf:type owl:NamedIndividual ,
                            :taskDependency ;
                :diagnosisName "${taskRequest.diagnosis}" ;
                :taskDependent "${taskRequest.taskName}" ;
                :taskToWait ?taskToWait .
            """.trimIndent()

        triplestoreService.replaceContentIgnoringSpaces(path, oldContent, "")

        return ResponseEntity.ok("Task dependency deleted")
    }
}