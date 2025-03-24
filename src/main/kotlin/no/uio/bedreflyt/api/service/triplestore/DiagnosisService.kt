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
    replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    @CachePut("diagnosis", key = "#diagnosisName")
    fun createDiagnosis(diagnosisName: String) : Boolean {
        val query = """
            PREFIX bedreflyt: <$prefix>
            
            INSERT DATA {
                bedreflyt:$diagnosisName a :Diagnosis ;
                    bedreflyt:diagnosisCode "$diagnosisName" .
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
                    prog:Diagnosis_diagnosisCode ?name .
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
    fun getDiagnosisByName(diagnosis: String) : Diagnosis? {
        val query = """
            SELECT DISTINCT ?name WHERE {
                ?obj a prog:Diagnosis ;
                    prog:Diagnosis_diagnosisCode ?diagnosis .
                FILTER (?diagnosis = "$diagnosis")
            }"""

        val resultDiagnosis: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultDiagnosis.hasNext()) {
            return null
        }

        val solution: QuerySolution = resultDiagnosis.next()
        val name = solution.get("?name").asLiteral().toString()
        return Diagnosis(name)
    }

    @CacheEvict("diagnosis", key = "#diagnosisName")
    @CachePut("diagnosis", key = "#newDiagnosisName")
    fun updateDiagnosis(oldDiagnosisName: String, newDiagnosisName: String) : Boolean {
        val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE {
                bedreflyt:$oldDiagnosisName a :Diagnosis ;
                 bedreflyt:diagnosisCode "$oldDiagnosisName" .
            }
            
            INSERT {
                bedreflyt:$newDiagnosisName a :Diagnosis ;
                 bedreflyt:diagnosisCode "$newDiagnosisName" .
            }
            
            WHERE {
                bedreflyt:$oldDiagnosisName a :Diagnosis ;
                 bedreflyt:diagnosisCode "$oldDiagnosisName" .
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
            PREFIX bedreflyt: <$prefix>
            
            DELETE {
                bedreflyt:$diagnosisName a :Diagnosis ;
                 bedreflyt:diagnosisCode "$diagnosisName" .
            }
            
            WHERE {
                bedreflyt:$diagnosisName a :Diagnosis ;
                 bedreflyt:diagnosisCode "$diagnosisName" .
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