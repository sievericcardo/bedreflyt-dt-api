package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.MonitoringCategory
import no.uio.bedreflyt.api.types.MonitoringCategoryRequest
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
open class MonitoringCategoryService (
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

    @CachePut("monitoringCategories", key = "#monitoringCategoryRequest.description")
    open fun createCategory(monitoringCategoryRequest: MonitoringCategoryRequest): MonitoringCategory? {
        lock.writeLock().lock()
        try {
            val name = monitoringCategoryRequest.description.split(" ").joinToString("")

            val query = """
            PREFIX bedreflyt: <$prefix>
            
            INSERT DATA {
                bedreflyt:$name a bedreflyt:MonitoringCategory ;
                    bedreflyt:hasMonitoringCode ${monitoringCategoryRequest.category} ;
                    bedreflyt:monitoringName "${monitoringCategoryRequest.description}" .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                replConfig.regenerateSingleModel().invoke("monitoring categories")
                return MonitoringCategory(monitoringCategoryRequest.category, monitoringCategoryRequest.description)
            } catch (_: Exception) {
                return null
            }
        }  finally {
            lock.writeLock().unlock()
        }
    }

    @Cacheable("monitoringCategories")
    open fun getAllCategories() : List<MonitoringCategory>? {
        lock.readLock().lock()
        try {
            val categories = mutableListOf<MonitoringCategory>()

            val query =
                """
               SELECT DISTINCT ?description ?category WHERE {
                ?obj a prog:MonitoringCategory ;
                    prog:MonitoringCategory_description ?description ;
                    prog:MonitoringCategory_category ?category .
            }"""

            val resultRooms: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultRooms.hasNext()) {
                return null
            }

            while (resultRooms.hasNext()) {
                val solution: QuerySolution = resultRooms.next()
                val description = solution.get("description").toString()
                val category = solution.get("category").asLiteral().toString().split("^^")[0].toInt()

                categories.add(MonitoringCategory(category, description))
            }

            return categories
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable("monitoringCategories", key = "#category")
    open fun getCategoryByCategory(category: Int) : MonitoringCategory? {
        lock.readLock().lock()
        try {
            val query =
                """
               SELECT DISTINCT ?description WHERE {
                ?obj a prog:MonitoringCategory ;
                    prog:MonitoringCategory_description ?description ;
                    prog:MonitoringCategory_category $category .
            }"""

            val resultRooms: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultRooms.hasNext()) {
                return null
            }

            val solution: QuerySolution = resultRooms.next()
            val description = solution.get("description").toString()

            return MonitoringCategory(category, description)
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable("monitoringCategories", key = "#description")
    open fun getCategoryByDescription(description: String) : MonitoringCategory? {
        lock.readLock().lock()
        try {
            val query =
                """
               SELECT DISTINCT ?category WHERE {
                ?obj a prog:MonitoringCategory ;
                    prog:MonitoringCategory_description "$description" ;
                    prog:MonitoringCategory_category ?category .
            }"""

            val resultRooms: ResultSet = repl.interpreter!!.query(query)!!
            if (!resultRooms.hasNext()) {
                return null
            }

            val solution: QuerySolution = resultRooms.next()
            val category = solution.get("category").asLiteral().toString().split("^^")[0].toInt()

            return MonitoringCategory(category, description)
        } finally {
            lock.readLock().unlock()
        }
    }

    @CacheEvict("monitoringCategories", key = "#monitoringCategory.description")
    @CachePut("monitoringCategories", key = "#newDescription")
    open fun updateCategory(monitoringCategory: MonitoringCategory, newDescription: String) : MonitoringCategory? {
        lock.writeLock().lock()
        try {
            val oldName = monitoringCategory.description.split(" ").joinToString("")
            val newName = newDescription.split(" ").joinToString("")

            val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE {
                bedreflyt:$oldName a bedreflyt:MonitoringCategory ;
                    bedreflyt:hasMonitoringCode ${monitoringCategory.category} ;
                    bedreflyt:monitoringName "${monitoringCategory.description}" .
            }
            INSERT {
                bedreflyt:$newName a bedreflyt:MonitoringCategory ;
                    bedreflyt:hasMonitoringCode ${monitoringCategory.category} ;
                    bedreflyt:monitoringName "$newDescription" .
            }
            WHERE {
                bedreflyt:$oldName a bedreflyt:MonitoringCategory ;
                    bedreflyt:hasMonitoringCode ${monitoringCategory.category} ;
                    bedreflyt:monitoringName "${monitoringCategory.description}" .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                replConfig.regenerateSingleModel().invoke("monitoring categories")
                return MonitoringCategory(monitoringCategory.category, newDescription)
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @CacheEvict("monitoringCategories", allEntries = true)
    open fun deleteCategory(monitoringCategory: MonitoringCategory) : Boolean {
        lock.writeLock().lock()
        try {
            val name = monitoringCategory.description.split(" ").joinToString("")

            val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE {
                bedreflyt:$name a bedreflyt:MonitoringCategory ;
                    bedreflyt:hasMonitoringCode ${monitoringCategory.category} ;
                    bedreflyt:monitoringName "${monitoringCategory.description}" .
            }
            WHERE {
                bedreflyt:$name a bedreflyt:MonitoringCategory ;
                    bedreflyt:hasMonitoringCode ${monitoringCategory.category} ;
                    bedreflyt:monitoringName "${monitoringCategory.description}" .
            }
        """.trimIndent()

            val updateRequest: UpdateRequest = UpdateFactory.create(query)
            val fusekiEndpoint = "$tripleStore/update"
            val updateProcessor: UpdateProcessor = UpdateExecutionFactory.createRemote(updateRequest, fusekiEndpoint)

            try {
                updateProcessor.execute()
                replConfig.regenerateSingleModel().invoke("monitoring categories")
                return true
            } catch (_: Exception) {
                return false
            }
        } finally {
            lock.writeLock().unlock()
        }
    }
}