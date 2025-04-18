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

@Service
open class MonitoringCategoryService (
    replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties
) {

    @Autowired
    private lateinit var cacheManager: CacheManager

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    @CachePut("monitoringCategories", key = "#monitoringCategoryRequest.description")
    open fun createCategory(monitoringCategoryRequest: MonitoringCategoryRequest): MonitoringCategory? {
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
            return MonitoringCategory(monitoringCategoryRequest.category, monitoringCategoryRequest.description)
        } catch (e: Exception) {
            return null
        }
    }

    @Cacheable("monitoringCategories")
    open fun getAllCategories() : List<MonitoringCategory>? {
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
    }

    @Cacheable("monitoringCategories", key = "#category")
    open fun getCategoryByCategory(category: Int) : MonitoringCategory? {
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
    }

    @Cacheable("monitoringCategories", key = "#description")
    open fun getCategoryByDescription(description: String) : MonitoringCategory? {
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
    }

    @CacheEvict("monitoringCategories", key = "#monitoringCategory.description")
    @CachePut("monitoringCategories", key = "#newDescription")
    open fun updateCategory(monitoringCategory: MonitoringCategory, newDescription: String) : MonitoringCategory? {
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
            return MonitoringCategory(monitoringCategory.category, newDescription)
        } catch (e: Exception) {
            return null
        }
    }

    @CacheEvict("monitoringCategories", key = "#monitoringCategory.description")
    open fun deleteCategory(monitoringCategory: MonitoringCategory) : Boolean {
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
            return true
        } catch (e: Exception) {
            return false
        }
    }
}