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

class CityService (
    replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    fun createCity(request: CityRequest) : Boolean {
        val query = """
            PREFIX bedreflyt: <$prefix>
            
            INSERT DATA {
                bedreflyt:${request.cityName} a :City ;
                    bedreflyt:cityName "${request.cityName}" .
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

    fun getAllCities() : List<City>? {
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

    fun getCityByName(cityName: String) : City? {
        val query = """
            PREFIX bedreflyt: <$prefix>
            
            SELECT DISTINCT ?cityName WHERE {
                ?city a prog:City ;
                    prog:City_cityName "$cityName" .
            }
        """.trimIndent()

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!
        if(!resultSet.hasNext()) {
            return null
        }

        val result: QuerySolution = resultSet.next()
        return City(result.get("cityName").toString())
    }

    fun updateCity(cityName: String, newCityName: String) : Boolean {
        val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE {
                bedreflyt:$cityName a City ;
                    bedreflyt:cityName $cityName .
            }
            INSERT {
                bedreflyt:$newCityName a :City ;
                    bedreflyt:cityName $newCityName .
            }
            WHERE {
                bedreflyt:$cityName a City ;
                    bedreflyt:cityName $cityName .
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

    fun deleteCity(cityName: String) : Boolean {
        val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE WHERE {
                bedreflyt:$cityName a City ;
                    bedreflyt:cityName $cityName .
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