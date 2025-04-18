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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.logging.Logger

@Service
open class CityService (
    replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
) {

    @Autowired
    private lateinit var cacheManager: CacheManager

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()
    private val log: Logger = Logger.getLogger(CityService::class.java.name)

    @Cacheable("cities")
    open fun createCity(request: CityRequest) : City? {
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
            return City(request.cityName)
        } catch (e: Exception) {
            return null
        }
    }

    @Cacheable("cities")
    open fun getAllCities() : List<City>? {
        val cities = mutableListOf<City>()

        val query =
            """
                SELECT DISTINCT ?cityName WHERE {
                    ?city a prog:City ;
                        prog:City_cityName ?cityName .
            }"""

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!
        if(!resultSet.hasNext()) {
            return null
        }

        while (resultSet.hasNext()) {
            val result: QuerySolution = resultSet.next()
            val cityName = result.get("cityName").toString()
            cities.add(City(cityName))
        }

        return cities
    }

    @Cacheable("cities")
    open fun getCityByName(cityName: String) : City? {
        log.info("Retrieving city $cityName")
        val query = """
            SELECT DISTINCT ?cityName WHERE {
                ?city a prog:City ;
                    prog:City_cityName ?cityName .
                FILTER (?cityName = "$cityName")
            }
        """.trimIndent()

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!
        if(!resultSet.hasNext()) {
            return null
        }

        val result: QuerySolution = resultSet.next()
        val name = result.get("cityName").toString()
        return City(name)
    }

    @CacheEvict("cities", allEntries = true)
    @CachePut("cities")
    open fun updateCity(cityName: String, newCityName: String) : City? {
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
            return City(newCityName)
        } catch (e: Exception) {
            return null
        }
    }

    @CacheEvict("cities", key = "#cityName")
    open fun deleteCity(cityName: String) : Boolean {
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
            return true
        } catch (e: Exception) {
            return false
        }
    }
}