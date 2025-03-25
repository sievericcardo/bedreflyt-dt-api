package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Floor
import no.uio.bedreflyt.api.types.FloorRequest
import no.uio.bedreflyt.api.types.UpdateFloorRequest
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
class FloorService (
    replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    @CachePut("floors", key = "#request.floorNumber")
    fun createFloor(request: FloorRequest) : Boolean {
        val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX brick: <https://brickschema.org/schema/Brick#>
            
            INSERT DATA {
                bedreflyt:Floor${request.floorNumber} a brick:Floor ;
                    bedreflyt:floorNumber ${request.floorNumber} .
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

    @Cacheable("floors")
    fun getAllFloors() : List<Floor>? {
        val floors = mutableListOf<Floor>()

        val query =
            """
                SELECT DISTINCT ?floorNumber WHERE {
                    ?floor a prog:Floor ;
                        prog:Floor_floorNumber ?floorNumber .
            }"""

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!
        if(!resultSet.hasNext()) {
            return null
        }

        while(resultSet.hasNext()) {
            val result: QuerySolution = resultSet.next()
            val floorNumber = result.get("floorNumber").asLiteral().int
            floors.add(Floor(floorNumber))
        }

        return floors
    }

    @Cacheable("floors", key = "#number")
    fun getFloorByNumber (number: Int) : Floor? {
        val query = """
            SELECT DISTINCT ?floorNumber WHERE {
                ?floor a prog:Floor ;
                    prog:Floor_floorNumber $number .
            }
        """.trimIndent()

        val resultSet: ResultSet = repl.interpreter!!.query(query)!!
        if(!resultSet.hasNext()) {
            return null
        }

        while(resultSet.hasNext()) {
            val result: QuerySolution = resultSet.next()
            val floorNumber = result.get("floorNumber").asLiteral().int
            if (floorNumber == number) {
                return Floor(floorNumber)
            }
        }

        return null
    }

    @CacheEvict("floors", key = "#request.floorNumber")
    @CachePut("floors", key = "#request.newFloorNumber")
    fun updateFloor(oldFloorNumber: Int, newFloorNumber: Int) : Boolean {
        val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX brick: <https://brickschema.org/schema/Brick#>
            
            DELETE {
                bedreflyt:Floor${oldFloorNumber} a brick:Floor ; 
                    bedreflyt:floorNumber $oldFloorNumber .
            }
            INSERT {
                bedreflyt:Floor${newFloorNumber} a brick:Floor ;
                    bedreflyt:floorNumber $newFloorNumber .
            }
            WHERE {
                bedreflyt:Floor${oldFloorNumber} a brick:Floor ;
                    bedreflyt:floorNumber $oldFloorNumber .
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

    @CacheEvict("floors", key = "#request.floorNumber")
    fun deleteFloor(floorNumber: Int) : Boolean {
        val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX brick: <https://brickschema.org/schema/Brick#>
            
            DELETE WHERE {
                bedreflyt:Floor${floorNumber} a brick:Floor ;
                    bedreflyt:floorNumber $floorNumber .
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