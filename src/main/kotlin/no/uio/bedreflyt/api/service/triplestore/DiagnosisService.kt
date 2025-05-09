package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Diagnosis
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
class DiagnosisService (
    private val replConfig: REPLConfig,
    private val triplestoreProperties: TriplestoreProperties
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    fun createDiagnosis(diagnosisName: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :diagnosis_$diagnosisName a :Diagnosis ;
                    :diagnosisName "$diagnosisName" .
            }"""

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    @Cacheable("diagnosis")
    fun getAllDiagnosis(): List<Diagnosis>? {
        val diagnosis: MutableList<Diagnosis> = mutableListOf()

        val query = """
            SELECT DISTINCT ?name WHERE {
                ?obj a prog:Diagnosis ;
                    prog:Diagnosis_diagnosisName ?name .
            }"""

        val resultDiagnosis: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultDiagnosis.hasNext()) {
            return null
        }

        while (resultDiagnosis.hasNext()) {
            val solution: QuerySolution = resultDiagnosis.next()
            val name = solution.get("?name").asLiteral().toString()
            diagnosis.add(Diagnosis(name))
        }

        return diagnosis
    }

    @Cacheable("diagnosis", key = "#diagnosis")
    fun getDiagnosisByName(diagnosisName: String) : Diagnosis? {
        val query = """
            SELECT DISTINCT ?name WHERE {
                ?obj a prog:Diagnosis ;
                    prog:Diagnosis_diagnosisName ?diagnosis .
                FILTER (?diagnosis = "$diagnosisName")
            }"""

        val resultDiagnosis: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultDiagnosis.hasNext()) {
            return null
        }

        val solution: QuerySolution = resultDiagnosis.next()
        val name = solution.get("?name").asLiteral().toString()
        return Diagnosis(name)
    }

    @CacheEvict(value = ["diagnosis"], key = "#oldDiagnosisName")
    @CachePut(value = ["diagnosis"], key = "#newDiagnosisName")
    fun updateDiagnosis(oldDiagnosisName: String, newDiagnosisName: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :diagnosis_$oldDiagnosisName a :Diagnosis ;
                 :diagnosisName "$oldDiagnosisName" .
            }
            
            INSERT {
                :diagnosis_$newDiagnosisName a :Diagnosis ;
                 :diagnosisName "$newDiagnosisName" .
            }
            
            WHERE {
                :diagnosis_$oldDiagnosisName a :Diagnosis ;
                 :diagnosisName "$oldDiagnosisName" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    @CacheEvict("diagnosis", key = "#diagnosisName")
    fun deleteDiagnosis(diagnosisName: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :diagnosis_$diagnosisName a :Diagnosis ;
                 :diagnosisName "$diagnosisName" .
            }
            
            WHERE {
                :diagnosis_$diagnosisName a :Diagnosis ;
                 :diagnosisName "$diagnosisName" .
            }
        """.trimIndent()

        val updateRequest: UpdateRequest = UpdateFactory.create(query)
        val fusekiEndpoint = "$tripleStore/update"
        val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

        try {
            updateProcessor.execute()
            return true
        } catch (e: Exception) {
            return false
        }
    }
}