package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.City
import no.uio.bedreflyt.api.types.CityRequest
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.apache.jena.update.UpdateRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantReadWriteLock

@Service
open class CityService (
    private val replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()
    private val log: Logger = LoggerFactory.getLogger(CityService::class.java.name)
    private val lock = ReentrantReadWriteLock()

    @CacheEvict(value = ["cities"], allEntries = true)
    @CachePut("cities", key = "#request.cityName")
    open fun createCity(request: CityRequest) : City? {
        lock.writeLock().lock()
        try {
            val name = request.cityName.split(" ").joinToString("_")
            val query = """
            PREFIX bedreflyt: <$prefix>
            
            INSERT DATA {
                bedreflyt:${name} a bedreflyt:City ;
                    bedreflyt:cityName "${request.cityName}" .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                replConfig.regenerateSingleModel().invoke("cities")

                return City(request.cityName)
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @Cacheable(value = ["cities"], key = "'allCities'")
    open fun getAllCities() : List<City>? {
        lock.readLock().lock()
        try {
            val cities = mutableListOf<City>()

            val query =
                """
                SELECT DISTINCT ?cityName WHERE {
                    ?city a prog:City ;
                        prog:City_cityName ?cityName .
            }"""

            val resultSet: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultSet.hasNext()) {
                return null
            }

            while (resultSet.hasNext()) {
                val result: QuerySolution = resultSet.next()
                val cityName = result.get("cityName").toString()
                cities.add(City(cityName))
            }

            return cities
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable("cities", key = "#cityName")
    open fun getCityByName(cityName: String) : City? {
        lock.readLock().lock()
        try {
            log.info("Retrieving city $cityName")
            val query = """
            SELECT DISTINCT ?cityName WHERE {
                ?city a prog:City ;
                    prog:City_cityName ?cityName .
                FILTER (?cityName = "$cityName")
            }
        """.trimIndent()

            val resultSet: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultSet.hasNext()) {
                return null
            }

            val result: QuerySolution = resultSet.next()
            val name = result.get("cityName").toString()

            return City(name)
        } finally {
            lock.readLock().unlock()
        }
    }

    @CacheEvict(value = ["cities"], allEntries = true)
    @CachePut("cities", key = "#newCityName")
    open fun updateCity(cityName: String, newCityName: String) : City? {
        lock.writeLock().lock()
        try {
            val name = cityName.split(" ").joinToString("_")
            val newName = newCityName.split(" ").joinToString("_")
            val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE {
                bedreflyt:$name a bedreflyt:City ;
                    bedreflyt:cityName "$cityName" .
            }
            INSERT {
                bedreflyt:$newName a bedreflyt:City ;
                    bedreflyt:cityName "$newCityName" .
            }
            WHERE {
                bedreflyt:$name a bedreflyt:City ;
                    bedreflyt:cityName "$cityName" .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                replConfig.regenerateSingleModel().invoke("cities")
                return City(newCityName)
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @CacheEvict(value = ["cities"], allEntries = true)
    open fun deleteCity(cityName: String) : Boolean {
        lock.writeLock().lock()
        try {
            val name = cityName.split(" ").joinToString("_")
            val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE WHERE {
                bedreflyt:$name a bedreflyt:City ;
                    bedreflyt:cityName "$cityName" .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                replConfig.regenerateSingleModel().invoke("cities")

                return true
            } catch (_: Exception) {
                return false
            }
        } finally {
            lock.writeLock().unlock()
        }
    }
}