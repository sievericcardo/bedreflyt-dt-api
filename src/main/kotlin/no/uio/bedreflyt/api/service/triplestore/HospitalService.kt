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
import java.util.concurrent.locks.ReentrantReadWriteLock

@Service
open class HospitalService (
    private val replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
    private val cityService: CityService
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()
    private val lock = ReentrantReadWriteLock()

    @CacheEvict(value = ["hospitals"], allEntries = true)
    @CachePut("hospitals", key = "#request.hospitalCode")
    open fun createHospital(request: HospitalRequest) : Hospital? {
        lock.writeLock().lock()
        try {
            val name = request.hospitalName.split(" ").joinToString("")
            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX brick: <https://brickschema.org/schema/Brick#>
            
            INSERT DATA {
                bedreflyt:$name a brick:Hospital ;
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
                replConfig.regenerateSingleModel().invoke("hospitals")
                return Hospital(request.hospitalName, request.hospitalCode, cityService.getCityByName(request.city)!!)
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @Cacheable(value = ["hospitals"], key = "'allHospitals'")
    open fun getAllHospitals() : List<Hospital>? {
        lock.readLock().lock()
        try {
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

            if (!results.hasNext()) {
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
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable("hospitals", key = "#hospitalCode")
    open fun getHospitalByCode (hospitalCode: String) : Hospital? {
        lock.readLock().lock()
        try {
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

            if (!results.hasNext()) {
                return null
            }

            val result: QuerySolution = results.nextSolution()
            val hospitalName = result.get("hospitalName").toString()
            val city = cityService.getCityByName(result.get("cityName").toString()) ?: return null

            return Hospital(hospitalName, hospitalCode, city)
        } finally {
            lock.readLock().unlock()
        }
    }

    @CacheEvict(value = ["hospitals"], allEntries = true)
    @CachePut("hospitals", key = "#hospital.hospitalCode")
    open fun updateHospital (hospital: Hospital, newHospitalName: String) : Hospital? {
        lock.writeLock().lock()
        try {
            val oldName = hospital.hospitalName.split(" ").joinToString("")
            val newName = newHospitalName.split(" ").joinToString("")

            val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX brick: <https://brickschema.org/schema/Brick#>
            
            DELETE {
                bedreflyt:$oldName a brick:Hospital ;
                    bedreflyt:hasCity bedreflyt:${hospital.hospitalCity.cityName} ;
                    bedreflyt:hospitalCode "${hospital.hospitalCode}" ;
                    bedreflyt:hospitalName "${hospital.hospitalName}" .
            }
            INSERT {
                bedreflyt:$newName a brick:Hospital ;
                    bedreflyt:hasCity bedreflyt:${hospital.hospitalCity.cityName} ;
                    bedreflyt:hospitalCode "${hospital.hospitalCode}" ;
                    bedreflyt:hospitalName "$newHospitalName" .
            }
            WHERE {
                bedreflyt:$oldName a brick:Hospital ;
                    bedreflyt:hasCity bedreflyt:${hospital.hospitalCity.cityName} ;
                    bedreflyt:hospitalCode "${hospital.hospitalCode}" ;
                    bedreflyt:hospitalName "${hospital.hospitalName}" .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                replConfig.regenerateSingleModel().invoke("hospitals")
                return Hospital(newHospitalName, hospital.hospitalCode, hospital.hospitalCity)
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @CacheEvict(value = ["hospitals"], allEntries = true)
    open fun deleteHospital (hospitalCode: String) : Boolean {
        lock.writeLock().lock()
        try {
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
            } catch (_: Exception) {
                return false
            }
        } finally {
            lock.writeLock().unlock()
        }
    }
}