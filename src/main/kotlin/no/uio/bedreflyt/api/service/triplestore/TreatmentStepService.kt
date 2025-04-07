package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.TreatmentStep
import org.apache.jena.query.ResultSet
import org.springframework.stereotype.Service
import org.springframework.cache.annotation.Cacheable
import java.util.logging.Logger

@Service
class TreatmentStepService(
    replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
    private val monitoringCategoryService: MonitoringCategoryService,
    private val taskService: TaskService
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val repl = replConfig.repl()
    private val log : Logger = Logger.getLogger(TreatmentStepService::class.java.name)

    @Cacheable("treatment-steps")
    fun getTreatmentStep(stepName: String, treatmentName: String): TreatmentStep? {
        val query = """
        SELECT ?previousTask ?nextTask ?stepNumber ?monitoringCategory ?staffLoad ?averageDuration WHERE {
            ?step a prog:TreatmentStep ;
                prog:TreatmentStep_treatmentName "$treatmentName" ;
                prog:TreatmentStep_task ?taskObj ;
                prog:TreatmentStep_stepNumber ?stepNumber ;
                prog:TreatmentStep_monitoringCategory ?monitoringCategoryObj ;
                prog:TreatmentStep_staffLoad ?staffLoad ;
                prog:TreatmentStep_averageDuration ?averageDuration ;
                prog:TreatmentStep_previousTask ?previousTask ;
                prog:TreatmentStep_nextTask ?nextTask .
                
            ?taskObj a prog:Task ;
                prog:Task_taskName "$stepName" .
                
            ?monitoringCategoryObj a prog:MonitoringCategory ;
                prog:MonitoringCategory_description ?monitoringCategory .
        }
        ORDER BY ?stepNumber
    """.trimIndent()

        log.info("Query: $query")

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!
        if (!resultSet.hasNext()) {
            return null
        }

        val result = resultSet.next()
        val monitoringCategory = result.get("monitoringCategory").toString()
        val stepNumber = result.get("stepNumber").asLiteral().toString().split("^^")[0].toInt()
        val category = monitoringCategoryService.getCategoryByDescription(monitoringCategory) ?: return null
        val task = taskService.getTaskByTaskName(stepName) ?: return null

        val staffLoad = result.get("staffLoad").asLiteral().toString().split("^^")[0].toDouble()
        val averageDuration = result.get("averageDuration").asLiteral().toString().split("^^")[0].toDouble()
        val previousTaskName = result.get("previousTask")?.toString()
        val nextTaskName = result.get("nextTask")?.toString()

        log.info("Previous task: $previousTaskName Next task: $nextTaskName")

        return TreatmentStep(
            treatmentName = treatmentName,
            monitoringCategory = category,
            task = task,
            stepNumber = stepNumber,
            staffLoad = staffLoad,
            averageDuration = averageDuration,
            previousTask = previousTaskName,
            nextTask = nextTaskName
        )
    }

    @Cacheable("treatment-steps")
    fun getAllTreatmentSteps() : List<TreatmentStep>? {
        val steps = mutableListOf<TreatmentStep>()

        val query = """
        SELECT DISTINCT ?treatmentName ?previousTask ?nextTask ?stepNumber ?monitoringCategory ?task ?staffLoad ?averageDuration WHERE {
            ?step a prog:TreatmentStep ;
                prog:TreatmentStep_treatmentName ?treatmentName ;
                prog:TreatmentStep_task ?taskObj ;
                prog:TreatmentStep_stepNumber ?stepNumber ;
                prog:TreatmentStep_monitoringCategory ?monitoringCategoryObj ;
                prog:TreatmentStep_staffLoad ?staffLoad ;
                prog:TreatmentStep_averageDuration ?averageDuration ;
                prog:TreatmentStep_previousTask ?previousTask ;
                prog:TreatmentStep_nextTask ?nextTask .
                
            ?taskObj a prog:Task ;
                prog:Task_taskName ?task .
                
            ?monitoringCategoryObj a prog:MonitoringCategory ;
                prog:MonitoringCategory_description ?monitoringCategory .
        }
        ORDER BY ?stepNumber
    """.trimIndent()

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!
        if (!resultSet.hasNext()) {
            return null
        }

        while (resultSet.hasNext()) {
            val result = resultSet.next()
            val treatmentName = result.get("treatmentName").toString()
            val monitoringCategory = result.get("monitoringCategory").toString()
            val stepNumber = result.get("stepNumber").asLiteral().toString().split("^^")[0].toInt()
            val category = monitoringCategoryService.getCategoryByDescription(monitoringCategory) ?: continue

            val taskObj = result.get("task").toString()
            val task = taskService.getTaskByTaskName(taskObj) ?: continue

            val staffLoad = result.get("staffLoad").asLiteral().toString().split("^^")[0].toDouble()
            val averageDuration = result.get("averageDuration").asLiteral().toString().split("^^")[0].toDouble()
            val previousTaskName = result.get("previousTask")?.toString()
            val nextTaskName = result.get("nextTask")?.toString()

            log.info("Previous task: $previousTaskName Next task: $nextTaskName")

            steps.add(
                TreatmentStep(
                    treatmentName = treatmentName,
                    monitoringCategory = category,
                    task = task,
                    stepNumber = stepNumber,
                    staffLoad = staffLoad,
                    averageDuration = averageDuration,
                    previousTask = previousTaskName,
                    nextTask = nextTaskName
                )
            )
        }

        return steps
    }

    @Cacheable("treatment-steps", key = "#treatmentName")
    fun getTreatmentStepsByTreatmentName(treatmentName: String) : List<TreatmentStep>? {
        val steps = mutableListOf<TreatmentStep>()
        val processedSteps = mutableSetOf<String>()

        val query = """
        SELECT DISTINCT ?previousTask ?nextTask ?stepNumber ?monitoringCategory ?task ?staffLoad ?averageDuration WHERE {
            ?step a prog:TreatmentStep ;
                prog:TreatmentStep_treatmentName "$treatmentName" ;
                prog:TreatmentStep_task ?taskObj ;
                prog:TreatmentStep_stepNumber ?stepNumber ;
                prog:TreatmentStep_monitoringCategory ?monitoringCategoryObj ;
                prog:TreatmentStep_staffLoad ?staffLoad ;
                prog:TreatmentStep_averageDuration ?averageDuration ;
                prog:TreatmentStep_previousTask ?previousTask ;
                prog:TreatmentStep_nextTask ?nextTask .
                
            ?taskObj a prog:Task ;
                prog:Task_taskName ?task .
                
            ?monitoringCategoryObj a prog:MonitoringCategory ;
                prog:MonitoringCategory_description ?monitoringCategory .
        }
        ORDER BY ?stepNumber
    """.trimIndent()

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!
        if (!resultSet.hasNext()) {
            return null
        }

        while (resultSet.hasNext()) {
            val result = resultSet.next()
            val taskObj = result.get("task").toString()

            if (processedSteps.contains(taskObj)) {
                continue
            }

            processedSteps.add(taskObj)

            val monitoringCategory = result.get("monitoringCategory").toString()
            val stepNumber = result.get("stepNumber").asLiteral().toString().split("^^")[0].toInt()
            val category = monitoringCategoryService.getCategoryByDescription(monitoringCategory) ?: continue

            val task = taskService.getTaskByTaskName(taskObj) ?: continue

            val staffLoad = result.get("staffLoad").asLiteral().toString().split("^^")[0].toDouble()
            val averageDuration = result.get("averageDuration").asLiteral().toString().split("^^")[0].toDouble()
            val previousTaskName = result.get("previousTask")?.toString()
            val nextTaskName = result.get("nextTask")?.toString()

            steps.add(
                TreatmentStep(
                    treatmentName = treatmentName,
                    monitoringCategory = category,
                    task = task,
                    stepNumber = stepNumber,
                    staffLoad = staffLoad,
                    averageDuration = averageDuration,
                    previousTask = previousTaskName,
                    nextTask = nextTaskName
                )
            )
        }

        return steps
    }
}