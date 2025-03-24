package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Hospital
import no.uio.bedreflyt.api.types.HospitalRequest
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
class HospitalService (
    replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
    private val cityService: CityService
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    @CachePut("hospitals", key = "#request.hospitalCode")
    fun createHospital(request: HospitalRequest) : Boolean {
        val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX brick: <https://brickschema.org/schema/Brick#>
            
            INSERT DATA {
                bedreflyt:${request.hospitalName} a brick:Hospital ;
                    bedreflyt:hasCity bedreflyt:${request.city} ;
                    bedreflyt:hospitalCode "${request.hospitalCode}" ;
                    bedreflyt:hospitalName "${request.hospitalName}" .
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

    @Cacheable("hospitals")
    fun getAllHospitals() : List<Hospital>? {
        val hospitals = mutableListOf<Hospital>()

        val query =
            """
                SELECT DISTINCT ?hospitalName ?hospitalCode ?cityName WHERE {
                    ?hospital a prog:Hospital ;
                        prog:Hospital_hospitalName ?hospitalName ;
                        prog:Hospital_hospitalCode ?hospitalCode ;
                        prog:Hospital_hospitalCity ?city .
                    ?city a prog:City ;
                        prog:City_cityName ?cityName .
                }
            """.trimIndent()

        val results: ResultSet = repl.interpreter!!.query(query)!!

        if(!results.hasNext()) {
            return null
        }

        while (results.hasNext()) {
            val result: QuerySolution = results.nextSolution()
            val hospitalName = result.get("hospitalName").toString()
            val hospitalCode = result.get("hospitalCode").toString()
            val city = cityService.getCityByName(result.get("cityName").toString()) ?: continue

            hospitals.add(Hospital(hospitalName, hospitalCode, city))
        }

        return hospitals
    }

    @Cacheable("hospitals", key = "#hospitalCode")
    fun getHospitalByCode (hospitalCode: String) : Hospital? {
        val query = """
            SELECT DISTINCT ?hospitalName ?cityName WHERE {
                ?hospital a prog:Hospital ;
                    prog:Hospital_hospitalName ?hospitalName ;
                    prog:Hospital_hospitalCode "$hospitalCode" ;
                    prog:Hospital_hospitalCity ?city .
                ?city a prog:City ;
                    prog:City_cityName ?cityName .
            }
        """.trimIndent()

        val results: ResultSet = repl.interpreter!!.query(query)!!

        if(!results.hasNext()) {
            return null
        }

        val result: QuerySolution = results.nextSolution()
        val hospitalName = result.get("hospitalName").toString()
        val city = cityService.getCityByName(result.get("cityName").toString()) ?: return null

        return Hospital(hospitalName, hospitalCode, city)
    }

    @CacheEvict("hospitals", key = "#hospitalCode")
    @CachePut("hospitals", key = "#newHospitalName")
    fun updateHospital (hospital: Hospital, newHospitalName: String) : Boolean {
        val oldName = hospital.hospitalName.split(" ").joinToString(" ")
        val newName = newHospitalName.split(" ").joinToString(" ")

        val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX brick: <https://brickschema.org/schema/Brick#>
            
            DELETE {
                bedreflyt:$oldName a brick:Hospital ;
                    bedreflyt:hospitalName "$oldName" .
            }
            INSERT {
                bedreflyt:$newName a brick:Hospital ;
                    bedreflyt:hospitalName "$newName" .
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

    @CacheEvict("hospitals", key = "#hospitalCode")
    fun deleteHospital (hospitalCode: String) : Boolean {
        val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX brick: <https://brickschema.org/schema/Brick#>
            
            DELETE {
                ?hospital a brick:Hospital ;
                    bedreflyt:hospitalCode "$hospitalCode" .
            }
            WHERE {
                ?hospital a brick:Hospital ;
                    bedreflyt:hospitalCode "$hospitalCode" .
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