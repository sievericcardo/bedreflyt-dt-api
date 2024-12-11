package no.uio.bedreflyt.api.controller.triplestore

import no.uio.bedreflyt.api.model.triplestore.Task
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
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

data class UpdateTreatmentRequest (
    val oldDiagnosis: String,
    val newDiagnosis: String,
    val oldTask: String,
    val newTask: TaskOrString,
    val oldDependsOn: String,
    val newDependsOn: String
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

    @Operation(summary = "Create a new treatment")
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
}