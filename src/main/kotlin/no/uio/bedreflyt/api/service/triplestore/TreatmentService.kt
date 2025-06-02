package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Treatment
import no.uio.bedreflyt.api.model.triplestore.TreatmentStep
import no.uio.bedreflyt.api.types.TreatmentRequest
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.random.Random

@Service
open class TreatmentService(
    private val replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
    private val diagnosisService: DiagnosisService,
    private val treatmentStepService: TreatmentStepService
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val repl = replConfig.repl()
    private val log: Logger = LoggerFactory.getLogger(TreatmentService::class.java.name)
    private val lock = ReentrantReadWriteLock()

    @CachePut("treatments", key = "#request.treatmentName")
    open fun createTreatment (request: TreatmentRequest) : Treatment? {
        lock.writeLock().lock()
        try {
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
                PREFIX bedreflyt: <$prefix>
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
                    log.info("Treatment created")
                } catch (_: Exception) {
                    return null
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
                log.info("Steps created")

                replConfig.regenerateSingleModel().invoke("treatments")
                return Treatment(
                    treatmentName = request.treatmentName,
                    treatmentDescription = null,
                    diagnosis = diagnosisService.getDiagnosisByName(request.diagnosis)!!,
                    frequency = request.frequency,
                    weight = request.weight,
                    firstTaskName = request.steps.first().task.taskName,
                    lastTaskName = request.steps.last().task.taskName
                )
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @Cacheable("treatments", key = "'allTreatments'")
    open fun getAllTreatments(): List<Pair<Treatment, List<TreatmentStep>>>? {
        lock.readLock().lock()
        try {
            val treatments = mutableListOf<Pair<Treatment, List<TreatmentStep>>>()

            val query = """            
        SELECT DISTINCT ?treatmentName ?diagnosis ?frequency ?weight ?firstTaskName ?lastTaskName WHERE {
            ?treatment a prog:Treatment ;
                prog:Treatment_firstTask ?firstStep ;
                prog:Treatment_lastTask ?lastStep ;
                prog:Treatment_treatmentName ?treatmentName ;
                prog:Treatment_diagnosis ?diagnosisObj ;
                prog:Treatment_frequency ?frequency ;
                prog:Treatment_weight ?weight .
                
           ?firstStep a prog:TreatmentStep ;
                prog:TreatmentStep_treatmentName ?treatmentName ;
                prog:TreatmentStep_task ?firstTaskObj .
           ?firstTaskObj a prog:Task ;
                prog:Task_taskName ?firstTaskName .
                
           ?lastStep a prog:TreatmentStep ;
                prog:TreatmentStep_treatmentName ?treatmentName ;
                prog:TreatmentStep_task ?lastTaskObj .
           ?lastTaskObj a prog:Task ;
                prog:Task_taskName ?lastTaskName .
                
           ?diagnosisObj a prog:Diagnosis ;
                prog:Diagnosis_diagnosisCode ?diagnosis .
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

                val frequency = result.get("frequency").asLiteral().toString().split("^^")[0].toDouble()
                val weight = result.get("weight").asLiteral().toString().split("^^")[0].toDouble()
                val firstTaskName = result.get("firstTaskName").toString()
                val lastTaskName = result.get("lastTaskName").toString()

                val treatment = Treatment(
                    treatmentName = treatmentName,
                    treatmentDescription = null,
                    diagnosis = diagnosisObj,
                    frequency = frequency,
                    weight = weight,
                    firstTaskName = firstTaskName,
                    lastTaskName = lastTaskName
                )

                val steps = treatmentStepService.getTreatmentStepsByTreatmentName(treatmentName)!!
                treatments.add(Pair(treatment, steps))
            }

            return treatments
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable("treatments", key = "#treatmentName")
    open fun getTreatmentsByTreatmentName(treatmentName: String) : Pair<Treatment, List<TreatmentStep>>? {
        lock.readLock().lock()
        try {
            val query = """
        SELECT DISTINCT ?diagnosis ?frequency ?weight ?firstTask ?lastTask WHERE {
            ?treatment a prog:Treatment ;
                prog:Treatment_firstTask ?firstStep ;
                prog:Treatment_lastTask ?lastStep ;
                prog:Treatment_treatmentName $treatmentName ;
                prog:Treatment_diagnosis ?diagnosisObj ;
                prog:Treatment_frequency ?frequency ;
                prog:Treatment_weight ?weight .
                
            ?firstStep a prog:TreatmentStep ;
                prog:TreatmentStep_treatmentName $treatmentName ;
                prog:TreatmentStep_task ?firstTaskObj .
           ?firstTaskObj a prog:Task ;
                prog:Task_taskName ?firstTask .
                
           ?lastStep a prog:TreatmentStep ;
                prog:TreatmentStep_treatmentName ?treatmentName ;
                prog:TreatmentStep_task ?lastTaskObj .
           ?lastTaskObj a prog:Task ;
                prog:Task_taskName ?lastTask .
                
            ?diagnosisObj a prog:Diagnosis ;
                prog:Diagnosis_diagnosisCode ?diagnosis .
        }
    """.trimIndent()

            val resultSet: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultSet.hasNext()) {
                return null
            }

            val result = resultSet.next()
            val diagnosis = result.get("diagnosis").toString()
            val diagnosisObj = diagnosisService.getDiagnosisByName(diagnosis) ?: return null

            val frequency = result.get("frequency").asLiteral().toString().split("^^")[0].toDouble()
            val weight = result.get("weight").asLiteral().toString().split("^^")[0].toDouble()
            val firstTaskName = result.get("firstTask").toString()
            val lastTaskName = result.get("lastTask").toString()

            val treatment = Treatment(
                treatmentName = treatmentName,
                treatmentDescription = null,
                diagnosis = diagnosisObj,
                frequency = frequency,
                weight = weight,
                firstTaskName = firstTaskName,
                lastTaskName = lastTaskName
            )

            val steps = treatmentStepService.getTreatmentStepsByTreatmentName(treatmentName)!!
            return Pair(treatment, steps)
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable("treatments", key = "'treatmentsByDiagnosis_' + #diagnosisName")
    open fun getTreatmentByDiagnosisName(diagnosisName: String) : List<Treatment>? {
        lock.readLock().lock()
        try {
            val treatments = mutableListOf<Treatment>()
            val query = """
        SELECT DISTINCT ?treatmentName ?frequency ?weight ?firstTask ?lastTask WHERE {
            ?treatment a prog:Treatment ;
                prog:Treatment_firstTask ?firstStep ;
                prog:Treatment_lastTask ?lastStep ;
                prog:Treatment_treatmentName ?treatmentName ;
                prog:Treatment_diagnosis ?diagnosis ;
                prog:Treatment_frequency ?frequency ;
                prog:Treatment_weight ?weight .
                
           ?firstStep a prog:TreatmentStep ;
                prog:TreatmentStep_treatmentName ?treatmentName ;
                prog:TreatmentStep_task ?firstTaskObj .
           ?firstTaskObj a prog:Task ;
                prog:Task_taskName ?firstTask .
                
           ?lastStep a prog:TreatmentStep ;
                prog:TreatmentStep_treatmentName ?treatmentName ;
                prog:TreatmentStep_task ?lastTaskObj .
           ?lastTaskObj a prog:Task ;
                prog:Task_taskName ?lastTask .
                
           ?diagnosis a prog:Diagnosis;
                prog:Diagnosis_diagnosisCode "$diagnosisName" .
        }
    """.trimIndent()

            val resultSet: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultSet.hasNext()) {
                return null
            }

            while (resultSet.hasNext()) {
                val result = resultSet.next()
                val treatmentName = result.get("treatmentName").toString()
                val frequency = result.get("frequency").asLiteral().toString().split("^^")[0].toDouble()
                val weight = result.get("weight").asLiteral().toString().split("^^")[0].toDouble()
                val firstTaskName = result.get("firstTask").toString()
                val lastTaskName = result.get("lastTask").toString()

                val treatment = Treatment(
                    treatmentName = treatmentName,
                    treatmentDescription = null,
                    diagnosis = diagnosisService.getDiagnosisByName(diagnosisName) ?: continue,
                    frequency = frequency,
                    weight = weight,
                    firstTaskName = firstTaskName,
                    lastTaskName = lastTaskName
                )

                treatments.add(treatment)
            }

            return treatments
        } finally {
            lock.readLock().unlock()
        }
    }

    private fun <T> List<T>.weightedChoice(weight: (T) -> Double): T {
        val totalWeights = this.sumOf(weight)
        val threshold: Double = Random.nextDouble(totalWeights)
        var seen: Double = 0.toDouble()
        for (elem in this) {
            seen += weight(elem)
            if (seen >= threshold) {
                return elem
            }
        }
        return this.last()
    }

    @Cacheable("treatments", key = "'treatmentByDiagnosisAndMode_' + #diagnosisName + '_' + #mode")
    open fun getTreatmentByDiagnosisAndMode(diagnosisName: String, mode: String) : Treatment {
        lock.readLock().lock()
        try {
            val treatments: List<Treatment> = getTreatmentByDiagnosisName(diagnosisName)
                ?: throw IllegalArgumentException("No treatments found for diagnosis $diagnosisName")

            return when (mode) {
                "worst" -> {
                    treatments.maxByOrNull { it.weight }!!
                }

                "common" -> {
                    treatments.maxByOrNull { it.frequency }!!
                }

                "random" -> {
                    treatments.random()
                }

                "sample" -> {
                    treatments.weightedChoice { it.frequency }
                }

                else -> {
                    throw IllegalArgumentException("Unrecognized mode: should be one of \"worst\", \"common\", \"random\" or \"sample\"")
                }
            }
        } finally {
            lock.readLock().unlock()
        }
    }

    @CacheEvict(value = ["treatments"], allEntries = true)
    open fun deleteTreatment(treatmentName: String) : Boolean {
        lock.writeLock().lock()
        try {
            val name = treatmentName.split(" ").joinToString("")
            val query = """
            PREFIX bedreflyt: <http://www.smolang.org/bedreflyt/>
            
            DELETE {
                bedreflyt:$name ?p ?o .
                ?treatment a bedreflyt:TreatmentStep ;
                    bedreflyt:stepOfTreatment bedreflyt:$name .
            }
            WHERE {
                bedreflyt:$name ?p ?o .
                ?treatment a bedreflyt:TreatmentStep ;
                    bedreflyt:stepOfTreatment bedreflyt:$name .
            }
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
        } finally {
            lock.writeLock().unlock()
        }
    }
}