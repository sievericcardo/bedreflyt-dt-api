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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantReadWriteLock

@Service
open class DiagnosisService (
    private val replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties
) {

    @Autowired
    private lateinit var cacheManager: CacheManager

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()
    private val lock = ReentrantReadWriteLock()

    open fun createDiagnosis(diagnosisName: String) : Diagnosis? {
        lock.writeLock().lock()
        try {
            val query = """
            PREFIX bedreflyt: <$prefix>
            
            INSERT DATA {
                bedreflyt:$diagnosisName a bedreflyt:Diagnosis ;
                    bedreflyt:diagnosisCode "$diagnosisName" .
            }"""

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                replConfig.regenerateSingleModel().invoke("diagnoses")
                val diagnosis = Diagnosis(diagnosisName)

                cacheManager.getCache("diagnosis")?.put(diagnosisName, diagnosis)
                return diagnosis
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    open fun getAllDiagnosis(): List<Diagnosis>? {
        lock.readLock().lock()
        try {
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

            cacheManager.getCache("diagnosis")?.put("allDiagnosis", diagnosis)
            return diagnosis
        } finally {
            lock.readLock().unlock()
        }
    }

    open fun getDiagnosisByName(diagnosis: String) : Diagnosis? {
        lock.readLock().lock()
        try {
            val query = """
            SELECT DISTINCT ?diagnosis WHERE {
                ?obj a prog:Diagnosis ;
                    prog:Diagnosis_diagnosisCode ?diagnosis .
                FILTER (?diagnosis = "$diagnosis")
            }"""

            val resultDiagnosis: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultDiagnosis.hasNext()) {
                return null
            }

            val solution: QuerySolution = resultDiagnosis.next()
            val name = solution.get("?diagnosis").asLiteral().toString()
            val diagnosis = Diagnosis(name)

            cacheManager.getCache("diagnosis")?.put(name, diagnosis)
            return diagnosis
        } finally {
            lock.readLock().unlock()
        }
    }

    open fun updateDiagnosis(oldDiagnosisName: String, newDiagnosisName: String) : Diagnosis? {
        lock.writeLock().lock()
        try {
            val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE {
                bedreflyt:$oldDiagnosisName a bedreflyt:Diagnosis ;
                 bedreflyt:diagnosisCode "$oldDiagnosisName" .
            }
            
            INSERT {
                bedreflyt:$newDiagnosisName a bedreflyt:Diagnosis ;
                 bedreflyt:diagnosisCode "$newDiagnosisName" .
            }
            
            WHERE {
                bedreflyt:$oldDiagnosisName a bedreflyt:Diagnosis ;
                 bedreflyt:diagnosisCode "$oldDiagnosisName" .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                val diagnosis = Diagnosis(newDiagnosisName)
                replConfig.regenerateSingleModel().invoke("diagnoses")

                cacheManager.getCache("diagnosis")?.put(newDiagnosisName, diagnosis)
                return diagnosis
            } catch (e: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    open fun deleteDiagnosis(diagnosisName: String) : Boolean {
        lock.writeLock().lock()
        try {
            val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE {
                bedreflyt:$diagnosisName a bedreflyt:Diagnosis ;
                 bedreflyt:diagnosisCode "$diagnosisName" .
            }
            
            WHERE {
                bedreflyt:$diagnosisName a bedreflyt:Diagnosis ;
                 bedreflyt:diagnosisCode "$diagnosisName" .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                replConfig.regenerateSingleModel().invoke("diagnoses")
                cacheManager.getCache("diagnosis")?.evict(diagnosisName)
                return true
            } catch (_: Exception) {
                return false
            }
        } finally {
            lock.writeLock().unlock()
        }
    }
}