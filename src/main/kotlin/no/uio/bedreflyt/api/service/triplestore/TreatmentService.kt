package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Treatment
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class TreatmentService (
    private val replConfig: REPLConfig,
    private val triplestoreProperties: TriplestoreProperties,
    private val diagnosisService: DiagnosisService
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    @CachePut("treatments", key = "#treatmentId + '_' + #diagnosis")
    fun createTreatment (treatmentId: String, diagnosis: String, frequency: Double, weight: Double) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :treatment_${treatmentId}_$diagnosis a :Treatment ;
                    :treatmentId "$treatmentId" ;
                    :diagnosis "$diagnosis" ;
                    :frequency "$frequency"^^xsd:double ;
                    :weight "$weight"^^xsd:double ;
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

    @Cacheable("treatments")
    fun getAllTreatments() : List<Treatment>? {
        val treatments: MutableList<Treatment> = mutableListOf()

        val query = """
            SELECT DISTINCT ?treatmentId ?diagnosis ?frequency ?weight WHERE {
                ?obj a prog:Treatment ;
                    prog:Treatment_treatmentId ?treatmentId ;
                    prog:Treatment_diagnosis ?diagnosis ;
                    prog:Treatment_frequency ?frequency ;
                    prog:Treatment_weight ?weight .
            }"""

        val resultSet : ResultSet = repl.interpreter!!.query(query) ?: return null

        if(!resultSet.hasNext()) {
            return null
        }

        while (resultSet.hasNext()) {
            val solution = resultSet.next()
            val treatmentId = solution.get("treatmentId").asLiteral().toString()
            val diagnosis = solution.get("diagnosis").asLiteral().toString()
            val frequency = solution.get("frequency").asLiteral().toString().split("^^")[0].toDouble()
            val weight = solution.get("weight").asLiteral().toString().split("^^")[0].toDouble()

            treatments.add(Treatment(treatmentId, diagnosis, frequency, weight))
        }

        return treatments
    }

    @Cacheable("treatments", key = "#diagnosis")
    fun getAllTreatmentsByDiagnosis(diagnosis: String) : List<Treatment> {
        val treatments: MutableList<Treatment> = mutableListOf()

        val query = """
            SELECT DISTINCT ?treatmentId ?diagnosis ?frequency ?weight WHERE {
                ?obj a prog:Treatment ;
                    prog:Treatment_treatmentId ?treatmentId ;
                    prog:Treatment_diagnosis ?diagnosis ;
                    prog:Treatment_frequency ?frequency ;
                    prog:Treatment_weight ?weight .
                FILTER (?diagnosis = "$diagnosis")
            }"""

        val resultSet : ResultSet = repl.interpreter!!.query(query)!!

        while (resultSet.hasNext()) {
            val solution = resultSet.next()
            val treatmentId = solution.get("treatmentId").asLiteral().string
            val frequency = solution.get("frequency").asLiteral().double
            val weight = solution.get("weight").asLiteral().double

            treatments.add(Treatment(treatmentId, diagnosis, frequency, weight))
        }

        return treatments
    }

    @Cacheable("treatments", key = "#treatment")
    fun getTreatmentById(treatment: String) : Treatment? {
        val query = """
            SELECT DISTINCT ?diagnosis ?frequency ?weight WHERE {
                ?obj a prog:Treatment ;
                    prog:Treatment_treatmentId ?treatment ;
                    prog:Treatment_diagnosis ?diagnosis ;
                    prog:Treatment_frequency ?frequency ;
                    prog:Treatment_weight ?weight .
                FILTER (?treatment = "$treatment")
            }"""

        val resultSet : ResultSet = repl.interpreter!!.query(query)!!

        if (!resultSet.hasNext()) {
            return null
        }

        val solution = resultSet.next()
        val diagnosis = solution.get("diagnosis").asLiteral().string
        val frequency = solution.get("frequency").asLiteral().double
        val weight = solution.get("weight").asLiteral().double

        return Treatment(treatment, diagnosis, frequency, weight)
    }

    @Cacheable("treatments", key = "#diagnosis + '_' + #treatment")
    fun getTreatmentByTreamentDiagnosis (diagnosis: String, treatment: String) : Treatment? {
        val query = """
            SELECT DISTINCT ?frequency ?weight WHERE {
                ?obj a prog:Treatment ;
                    prog:Treatment_treatmentId "$treatment" ;
                    prog:Treatment_diagnosis "$diagnosis" ;
                    prog:Treatment_frequency ?frequency ;
                    prog:Treatment_weight ?weight .
            }"""

        val resultSet : ResultSet = repl.interpreter!!.query(query)!!

        if (!resultSet.hasNext()) {
            return null
        }

        val solution = resultSet.next()
        val frequency = solution.get("frequency").asLiteral().double
        val weight = solution.get("weight").asLiteral().double

        return Treatment(treatment, diagnosis, frequency, weight)
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

    fun selectTreatmentByDiagnosisAndMode(diagnosis: String, mode: String): String {
        val treatments: List<Treatment> = getAllTreatmentsByDiagnosis(diagnosis)
        if (treatments.isEmpty()) {
            throw NoSuchElementException("No treatment found for diagnosis: $diagnosis")
        }
        return when (mode) {
            "worst" -> {
                treatments.maxByOrNull { it.weight }!!.treatmentId
            }

            "common" -> {
                treatments.maxByOrNull { it.frequency }!!.treatmentId
            }

            "random" -> {
                treatments.random().treatmentId
            }

            "sample" -> {
                treatments.weightedChoice { it.frequency }.treatmentId
            }

            else -> {
                throw IllegalArgumentException("Unrecognized mode: should be one of \"worst\", \"common\", \"random\" or \"sample\"")
            }
        }
    }

    @CacheEvict(value = ["treatments"], key = "#treatment.treatmentId + '_' + #treatment.diagnosis")
    @CachePut(value = ["treatments"], key = "#treatment.treatmentId + '_' + #treatment.diagnosis")
    fun updateTreatment(treatment: Treatment, newFrequency: Double, newWeight: Double) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :treatment_${treatment.treatmentId}_${treatment.diagnosis} a :Treatment ;
                    :treatmentId "${treatment.treatmentId}" ;
                    :diagnosis "${treatment.diagnosis}" ;
                    :frequency "${treatment.frequency}"^^xsd:double ;
                    :weight "${treatment.weight}"^^xsd:double ;
            }
            INSERT {
                :treatment_${treatment.treatmentId}_${treatment.diagnosis} a :Treatment ;
                    :treatmentId "${treatment.treatmentId}" ;
                    :diagnosis "${treatment.diagnosis}" ;
                    :frequency "$newFrequency"^^xsd:double ;
                    :weight "$newWeight"^^xsd:double ;
            }
            WHERE {
                :treatment_${treatment.treatmentId}_${treatment.diagnosis} a :Treatment ;
                    :treatmentId "${treatment.treatmentId}" ;
                    :diagnosis "${treatment.diagnosis}" ;
                    :frequency "${treatment.frequency}"^^xsd:double ;
                    :weight "${treatment.weight}"^^xsd:double ;
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

    @CacheEvict(value = ["treatments"], key = "#treatment.treatmentId + '_' + #treatment.diagnosis")
    fun deleteTreatment(treatment: Treatment) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :treatment_${treatment.treatmentId}_${treatment.diagnosis} a :Treatment ;
                    :treatmentId "${treatment.treatmentId}" ;
                    :diagnosis "${treatment.diagnosis}" ;
                    :frequency "${treatment.frequency}"^^xsd:double ;
                    :weight "${treatment.weight}"^^xsd:double ;
            }
            WHERE {
                :treatment_${treatment.treatmentId}_${treatment.diagnosis} a :Treatment ;
                    :treatmentId "${treatment.treatmentId}" ;
                    :diagnosis "${treatment.diagnosis}" ;
                    :frequency "${treatment.frequency}"^^xsd:double ;
                    :weight "${treatment.weight}"^^xsd:double ;
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