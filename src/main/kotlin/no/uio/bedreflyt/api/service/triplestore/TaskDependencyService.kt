package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.TaskDependency
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.apache.jena.update.UpdateRequest
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class TaskDependencyService (
    private val replConfig: REPLConfig,
    private val triplestoreProperties: TriplestoreProperties,
    private val taskService: TaskService
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    @CachePut("taskDependencies", key = "#taskName + '_' + #diagnosis + '_' + #treatment")
    fun createTaskDependency(treatment: String, diagnosis: String, taskName: String, dependencyName: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :taskDependency_${taskName}_${diagnosis}_${treatment} a :TaskDependency ;
                    :treatment "$treatment" ;
                    :diagnosisCode "$diagnosis" ;
                    :taskDependent "$taskName" ;
                    :taskToWait "$dependencyName" ;
            }
        """

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

    @Cacheable("taskDependencies")
    fun getAllTaskDependencies() : List<TaskDependency>? {
        val taskDependencies: MutableList<TaskDependency> = mutableListOf()

        val query = """
            SELECT DISTINCT ?treatment ?diagnosisName ?taskDependent ?taskToWait WHERE {
                ?obj a prog:TaskDependency ;
                    prog:TaskDependency_treatmentName ?treatment ;
                    prog:TaskDependency_diagnosisName ?diagnosisName ;
                    prog:TaskDependency_taskName ?taskDependent ;
                    prog:TaskDependency_taskDependency ?taskToWait .
            }"""

        val resultTaskDependencies: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultTaskDependencies.hasNext()) {
            return null
        }

        while (resultTaskDependencies.hasNext()) {
            val solution: QuerySolution = resultTaskDependencies.next()
            val treatmentName = solution.get("treatment").asLiteral().string
            val diagnosisName = solution.get("diagnosisName").asLiteral().string
            val taskDependent = solution.get("taskDependent").asLiteral().string
            val taskToWait = solution.get("taskToWait").asLiteral().string

            taskDependencies.add(TaskDependency(treatmentName, diagnosisName, taskDependent, taskToWait))
        }

        return taskDependencies
    }

    @Cacheable("taskDependencies", key ="#treatment")
    fun getTaskDependenciesByTreatment(treatment: String) : List<TaskDependency>? {
        val taskDependencies: MutableList<TaskDependency> = mutableListOf()

        val query = """
            SELECT DISTINCT ?diagnosisName ?taskDependent ?taskToWait WHERE {
                ?obj a prog:TaskDependency ;
                    prog:TaskDependency_treatmentName "$treatment" ;
                    prog:TaskDependency_diagnosisName ?diagnosisName ;
                    prog:TaskDependency_taskName ?taskDependent ;
                    prog:TaskDependency_taskDependency ?taskToWait .
            }"""

        val resultTaskDependencies: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultTaskDependencies.hasNext()) {
            return null
        }

        while (resultTaskDependencies.hasNext()) {
            val solution: QuerySolution = resultTaskDependencies.next()
            val diagnosisName = solution.get("diagnosisName").asLiteral().string
            val taskDependent = solution.get("taskDependent").asLiteral().string
            val taskToWait = solution.get("taskToWait").asLiteral().string

            taskDependencies.add(TaskDependency(treatment, diagnosisName, taskDependent, taskToWait))
        }

        return taskDependencies
    }

    @Cacheable("taskDependencies", key = "#treatment + '_' + #diagnosis")
    fun getTaskDependenciesByTreatmentAndDiagnosis(treatment: String, diagnosis: String) : List<TaskDependency>? {
        val taskDependencies: MutableList<TaskDependency> = mutableListOf()

        val query = """
            SELECT DISTINCT ?taskDependent ?taskToWait WHERE {
                ?obj a prog:TaskDependency ;
                    prog:TaskDependency_treatmentName "$treatment" ;
                    prog:TaskDependency_diagnosisName "$diagnosis" ;
                    prog:TaskDependency_taskName ?taskDependent ;
                    prog:TaskDependency_taskDependency ?taskToWait .
            }"""

        val resultTaskDependencies: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultTaskDependencies.hasNext()) {
            return null
        }

        while (resultTaskDependencies.hasNext()) {
            val solution: QuerySolution = resultTaskDependencies.next()
            val taskDependent = solution.get("taskDependent").asLiteral().string
            val taskToWait = solution.get("taskToWait").asLiteral().string

            taskDependencies.add(TaskDependency(treatment, diagnosis, taskDependent, taskToWait))
        }

        return taskDependencies
    }

    @CacheEvict(value = ["taskDependencies"], key = "#treatment + '_' + #diagnosis + '_' + #taskName")
    @CachePut("taskDependencies", key = "#treatment + '_' + #diagnosis + '_' + #taskName")
    fun updateTaskDependency(treatment: String, diagnosis: String, taskName: String, oldDependencyName: String, newDependencyName: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :taskDependency_${taskName}_${diagnosis}_${treatment} a :TaskDependency ;
                    :treatment "$treatment" ;
                    :diagnosisCode "$diagnosis" ;
                    :taskDependent "$taskName" ;
                    :taskToWait "$oldDependencyName" .
            }
            INSERT {
                :taskDependency_${taskName}_${diagnosis}_${treatment} a :TaskDependency ;
                    :treatment "$treatment" ;
                    :diagnosisCode "$diagnosis" ;
                    :taskDependent "$taskName" ;
                    :taskToWait "$newDependencyName" ;
            }
            WHERE {
                :taskDependency_${taskName}_${diagnosis}_${treatment} a :TaskDependency ;
                    :treatment "$treatment" ;
                    :diagnosisCode "$diagnosis" ;
                    :taskDependent "$taskName" ;
                    :taskToWait "$oldDependencyName" .
            }
        """

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

    @CacheEvict(value = ["taskDependencies"], key = "#treatment + '_' + #diagnosis + '_' + #taskName")
    fun deleteTaskDependency(treatment: String, diagnosis: String, taskName: String, taskToWait: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :taskDependency_${taskName}_${diagnosis}_${treatment} a :TaskDependency ;
                    :treatment "$treatment" ;
                    :diagnosisCode "$diagnosis" ;
                    :taskDependent "$taskName" ;
                    :taskToWait "$taskToWait" .
            }
            WHERE {
                :taskDependency_${taskName}_${diagnosis}_${treatment} a :TaskDependency ;
                    :treatment "$treatment" ;
                    :diagnosisCode "$diagnosis" ;
                    :taskDependent "$taskName" ;
                    :taskToWait "$taskToWait" .
            }
        """

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
}