package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Treatment
import no.uio.bedreflyt.api.model.triplestore.TreatmentStep
import no.uio.bedreflyt.api.types.TreatmentRequest
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class TreatmentService (
    private val replConfig: REPLConfig,
    private val triplestoreProperties: TriplestoreProperties,
    private val diagnosisService: DiagnosisService,
    private val monitoringCategoryService: MonitoringCategoryService,
    private val taskService: TaskService
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    fun createTreatment (request: TreatmentRequest) : Boolean {
        val treatmentName = request.treatmentName.split(" ").joinToString("")
        val steps = mutableListOf<String>()

        request.steps.forEachIndexed { index, treatmentStep ->
            val stepName = request.treatmentName.split(" ").joinToString("_") + "STEP_${index + 1}"
            steps.add(stepName)

            val previousStep = if (index == 0) {
                ""
            } else {
                "pko:previous bedreflyt:${treatmentName}STEP_${index}"
            }
            val nextStep = if (index == request.steps.size - 1) {
                ""
            } else {
                "pko:next bedreflyt:${treatmentName}STEP_${index + 2}"
            }

            val query = """
                PREFIX bedreflyt: <http://www.smolang.org/bedreflyt/>
                PREFIX time: <http://www.w3.org/2006/time#>
                PREFIX pko: <https://w3id.org/pko#>
                
                bedreflyt:$stepName a bedreflyt:TreatmentStep ;
                    bedreflyt:hasMonitoringStatus bedreflyt:${treatmentStep.monitoringCategory.description} ;
                    bedreflyt:hasTask bedreflyt:${treatmentStep.task.taskName} ;
                    $nextStep ;
                    $previousStep ;
                    bedreflyt:hasNurseBurden ${treatmentStep.staffLoad} ;
                    time:hours ${treatmentStep.averageDuration}
            """.trimIndent()

            val updateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
            } catch (e: Exception) {
                return false
            }
        }

        val query = """
            PREFIX bedreflyt: <http://www.smolang.org/bedreflyt/>
            PREFIX time: <http://www.w3.org/2006/time#>
            PREFIX pko: <https://w3id.org/pko#>
            
            bedreflyt:$treatmentName a pko:Procedure ;
                bedreflyt:hasFinalStep bedreflyt:${steps.last()} ;
                bedreflyt:isTreatmentProcOf bedreflyt:${request.diagnosis} ;
                pko:hasFirstStep bedreflyt:${steps.first()} ;
                pko:hasStep bedreflyt:${steps.joinToString(" , bedreflyt:")} ;
                bedreflyt:hasFrequency ${request.frequency} ;
                bedreflyt:hasWeight ${request.weight} .
        """.trimIndent()

        val updateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun getTreatmentStep(stepName: String, treatmentName: String): TreatmentStep? {
        val query = """
        SELECT ?previousTask ?nextTask ?monitoringCategory ?task ?staffLoad ?averageDuration WHERE {
            ?step a prog:TreatmentStep ;
                prog:TreatmentStep_treatmentName $treatmentName ;
                prog:TreatmentStep_task ?taskObj ;
                prog:TreatmentStep_monitoringCategory ?monitoringCategoryObj ;
                prog:TreatmentStep_staffLoad ?staffLoad ;
                prog:TreatmentStep_averageDuration ?averageDuration ;
                prog:TreatmentStep_previousTask ?previousTaskObj ;
                prog:TreatmentStep_nextTask ?nextTaskObj .
                
            ?taskObj a prog:Task ;
                prog:Task_taskName $stepName .
                
            ?monitoringCategoryObj a prog:MonitoringCategory ;
                prog:MonitoringCategory_description ?monitoringCategory .
                
            ?previousTaskObj a prog:TreatmentStep ;
                prog:TreatmentStep_treatmentName $treatmentName ;
                prog:TreatmentStep_task ?previousTask .
            ?previousTask a prog:Task .
                prog:Task_taskName ?previousTask .
                
            ?nextTaskObj a prog:TreatmentStep ;
                prog:TreatmentStep_treatmentName $treatmentName ;
                prog:TreatmentStep_task ?nextTask .
            ?nextTask a prog:Task .
                prog:Task_taskName ?nextTask .
        }
    """.trimIndent()

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!
        if (!resultSet.hasNext()) {
            return null
        }

        val result = resultSet.next()
        val monitoringCategory = result.get("monitoringCategory").toString()
        val category = monitoringCategoryService.getCategoryByDescription(monitoringCategory) ?: return null

        val taskObj = result.get("task").toString()
        val task = taskService.getTaskByTaskName(taskObj) ?: return null

        val staffLoad = result.get("staffLoad").toString().toDouble()
        val averageDuration = result.get("averageDuration").toString().toDouble()
        val previousTaskName = result.get("previousTask")?.toString()
        val nextTaskName = result.get("nextTask")?.toString()

        val previousTask = previousTaskName?.let { getTreatmentStep(it, treatmentName) }
        val nextTask = nextTaskName?.let { getTreatmentStep(it, treatmentName) }

        return TreatmentStep(
            treatmentName = treatmentName,
            monitoringCategory = category,
            task = task,
            staffLoad = staffLoad,
            averageDuration = averageDuration,
            previousTask = previousTask,
            nextTask = nextTask
        )
    }

    fun getAllTreatments(): List<Pair<Treatment, List<TreatmentStep>>>? {
        val treatments = mutableListOf<Pair<Treatment, List<TreatmentStep>>>()

        val query = """            
        SELECT DISTINCT ?treatmentName ?diagnosis ?frequency ?weight ?firstTask ?lastTask WHERE {
            ?treatment a prog:Treatment ;
                prog:Treatment_firstTask ?firstTask ;
                prog:Treatment_lastTask ?lastTask ;
                prog:Treatment_treatmentName ?treatmentName ;
                prog:Treatment_diagnosis ?diagnosisObj ;
                prog:Treatment_frequency ?frequency ;
                prog:Treatment_weight ?weight .
        }
    """.trimIndent()

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!
        if (!resultSet.hasNext()) {
            return null
        }

        while (resultSet.hasNext()) {
            val result = resultSet.next()
            val treatmentName = result.get("treatmentName").toString()
            val diagnosis = result.get("diagnosis").toString()
            val diagnosisObj = diagnosisService.getDiagnosisByName(diagnosis) ?: continue

            val frequency = result.get("frequency").toString().toDouble()
            val weight = result.get("weight").toString().toDouble()
            val firstTaskName = result.get("firstTask").toString()
            val lastTaskName = result.get("lastTask").toString()

            val firstTask = getTreatmentStep(firstTaskName, treatmentName) ?: continue
            val lastTask = getTreatmentStep(lastTaskName, treatmentName) ?: continue

            val treatment = Treatment(
                treatmentName = treatmentName,
                treatmentDescription = null,
                diagnosis = diagnosisObj,
                frequency = frequency,
                weight = weight,
                firstTask = firstTask,
                lastTask = lastTask
            )

            val steps = mutableListOf<TreatmentStep>()
            var currentStep: TreatmentStep? = firstTask
            while (currentStep != null) {
                steps.add(currentStep)
                currentStep = currentStep.nextTask
            }
            treatments.add(Pair(treatment, steps))
        }

        return treatments
    }

    fun getTreatmentsByTreamentName(treamentName: String) : Pair<Treatment, List<TreatmentStep>>? {
        val query = """
        SELECT DISTINCT ?diagnosis ?frequency ?weight ?firstTask ?lastTask WHERE {
            ?treatment a prog:Treatment ;
                prog:Treatment_firstTask ?firstTask ;
                prog:Treatment_lastTask ?lastTask ;
                prog:Treatment_treatmentName $treamentName ;
                prog:Treatment_diagnosis ?diagnosisObj ;
                prog:Treatment_frequency ?frequency ;
                prog:Treatment_weight ?weight .
        }
    """.trimIndent()

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!
        if (!resultSet.hasNext()) {
            return null
        }

        val result = resultSet.next()
        val diagnosis = result.get("diagnosis").toString()
        val diagnosisObj = diagnosisService.getDiagnosisByName(diagnosis) ?: return null

        val frequency = result.get("frequency").toString().toDouble()
        val weight = result.get("weight").toString().toDouble()
        val firstTaskName = result.get("firstTask").toString()
        val lastTaskName = result.get("lastTask").toString()

        val firstTask = getTreatmentStep(firstTaskName, treamentName) ?: return null
        val lastTask = getTreatmentStep(lastTaskName, treamentName) ?: return null

        val treatment = Treatment(
            treatmentName = treamentName,
            treatmentDescription = null,
            diagnosis = diagnosisObj,
            frequency = frequency,
            weight = weight,
            firstTask = firstTask,
            lastTask = lastTask
        )

        val steps = mutableListOf<TreatmentStep>()
        var currentStep: TreatmentStep? = firstTask
        while (currentStep != null) {
            steps.add(currentStep)
            currentStep = currentStep.nextTask
        }

        return Pair(treatment, steps)
    }

    fun deleteTreatment(treamentName: String) {
        val name = treamentName.split(" ").joinToString("")
        val query = """
            PREFIX bedreflyt: <http://www.smolang.org/bedreflyt/>
            
            DELETE {
                bedreflyt:$name ?p ?o .
                ?treatment a bedreflyt:TreatmentStep ;
                    bedreflyt:stepOfTreament bedreflyt:$name .
            }
            WHERE {
                bedreflyt:$name ?p ?o .
                ?treatment a bedreflyt:TreatmentStep ;
                    bedreflyt:stepOfTreament bedreflyt:$name .
            }
        """.trimIndent()

        val updateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
        } catch (e: Exception) {
            return
        }
    }
}