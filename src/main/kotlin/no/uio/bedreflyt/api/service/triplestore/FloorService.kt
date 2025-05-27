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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantReadWriteLock

@Service
open class FloorService (
    private val replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties,
) {

    @Autowired
    private lateinit var cacheManager: CacheManager

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()
    private val lock = ReentrantReadWriteLock()

    open fun createFloor(request: FloorRequest) : Floor? {
        lock.writeLock().lock()
        try {
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
                val floor = Floor(request.floorNumber)
                replConfig.regenerateSingleModel().invoke("floors")
                cacheManager.getCache("floors")?.put(request.floorNumber, floor)
                return floor
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    open fun getAllFloors() : List<Floor>? {
        lock.readLock().lock()
        try {
            val floors = mutableListOf<Floor>()

            val query =
                """
                SELECT DISTINCT ?floorNumber WHERE {
                    ?floor a prog:Floor ;
                        prog:Floor_floorNumber ?floorNumber .
            }"""

            val resultSet: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultSet.hasNext()) {
                return null
            }

            while (resultSet.hasNext()) {
                val result: QuerySolution = resultSet.next()
                val floorNumber = result.get("floorNumber").asLiteral().int
                floors.add(Floor(floorNumber))
            }

            cacheManager.getCache("floors")?.put("allFloors", floors)
            return floors
        } finally {
            lock.readLock().unlock()
        }
    }

    open fun getFloorByNumber (number: Int) : Floor? {
        lock.readLock().lock()
        try {
            val query = """
            SELECT DISTINCT ?floorNumber WHERE {
                ?floor a prog:Floor ;
                    prog:Floor_floorNumber ?floorNumber .
                FILTER (?floorNumber = $number)
            }
        """.trimIndent()

            val resultSet: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultSet.hasNext()) {
                return null
            }

            while (resultSet.hasNext()) {
                val result: QuerySolution = resultSet.next()
                val floorNumber = result.get("floorNumber").asLiteral().toString().split("^^")[0].toInt()
                if (floorNumber == number) {
                    val floor = Floor(floorNumber)
                    cacheManager.getCache("floors")?.put(number, floor)
                    return floor
                }
            }

            return null
        } finally {
            lock.readLock().unlock()
        }
    }

    open fun updateFloor(oldFloorNumber: Int, newFloorNumber: Int) : Floor? {
        lock.writeLock().lock()
        try {
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
                val floor = Floor(newFloorNumber)
                replConfig.regenerateSingleModel().invoke("floors")
                cacheManager.getCache("floors")?.evict(oldFloorNumber)
                cacheManager.getCache("floors")?.put(newFloorNumber, floor)

                return floor
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    open fun deleteFloor(floorNumber: Int) : Boolean {
        lock.writeLock().lock()
        try {
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
                replConfig.regenerateSingleModel().invoke("floors")
                cacheManager.getCache("floors")?.evict(floorNumber)

                return true
            } catch (_: Exception) {
                return false
            }
        } finally {
            lock.writeLock().unlock()
        }
    }
}