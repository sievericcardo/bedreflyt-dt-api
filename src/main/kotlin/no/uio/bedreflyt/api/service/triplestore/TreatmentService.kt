package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Treatment
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.springframework.stereotype.Service

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

    fun updateTreatment(treatmentId: String, diagnosis: String, oldFrequency: Double, oldWeight: Double, newFrequency: Double, newWeight: Double) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :treatment_${treatmentId}_$diagnosis a :Treatment ;
                    :treatmentId "$treatmentId" ;
                    :diagnosis "$diagnosis" ;
                    :frequency "$oldFrequency"^^xsd:double ;
                    :weight "$oldWeight"^^xsd:double ;
            }
            INSERT {
                :treatment_${treatmentId}_$diagnosis a :Treatment ;
                    :treatmentId "$treatmentId" ;
                    :diagnosis "$diagnosis" ;
                    :frequency "$newFrequency"^^xsd:double ;
                    :weight "$newWeight"^^xsd:double ;
            }
            WHERE {
                :treatment_${treatmentId}_$diagnosis a :Treatment ;
                    :treatmentId "$treatmentId" ;
                    :diagnosis "$diagnosis" ;
                    :frequency "$oldFrequency"^^xsd:double ;
                    :weight "$oldWeight"^^xsd:double ;
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

    fun deleteTreatment(treatmentId: String, diagnosis: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :treatment_${treatmentId}_$diagnosis a :Treatment ;
                    :treatmentId "$treatmentId" ;
                    :diagnosis "$diagnosis" ;
                    :frequency ?frequency ;
                    :weight ?weight ;
            }
            WHERE {
                :treatment_${treatmentId}_$diagnosis a :Treatment ;
                    :treatmentId "$treatmentId" ;
                    :diagnosis "$diagnosis" ;
                    :frequency ?frequency ;
                    :weight ?weight ;
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