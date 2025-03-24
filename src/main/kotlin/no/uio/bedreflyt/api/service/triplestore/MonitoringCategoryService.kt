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
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class MonitoringCategoryService (
    replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    @CachePut("monitoringCategories", key = "#monitoringCategoryRequest.description")
    fun createCategory(monitoringCategoryRequest: MonitoringCategoryRequest): Boolean {
        val name = monitoringCategoryRequest.description.split(" ").joinToString(" ")

        val query = """
            PREFIX bedreflyt: <$prefix>
            
            INSERT DATA {
                bedreflyt:$name a :MonitoringCategory ;
                    bedreflyt:hasMonitoringCode ${monitoringCategoryRequest.category} ;
                    bedreflyt:monitoringName "${monitoringCategoryRequest.description}" .
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

    @Cacheable("monitoringCategories")
    fun getAllCategoories() : List<MonitoringCategory>? {
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
    fun getCategoryByCategory(category: Int) : MonitoringCategory? {
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
    fun getCategoryByDescription(description: String) : MonitoringCategory? {
        val name = description.split(" ").joinToString(" ")

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
    fun updateCategory(monitoringCategory: MonitoringCategory, newCategory: Int, newDescription: String) : Boolean {
        val oldName = monitoringCategory.description.split(" ").joinToString(" ")
        val newName = newDescription.split(" ").joinToString(" ")

        val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE {
                bedreflyt:$oldName a :MonitoringCategory ;
                    bedreflyt:hasMonitoringCode ${monitoringCategory.category} ;
                    bedreflyt:monitoringName "${monitoringCategory.description}" .
            }
            INSERT {
                bedreflyt:$newName a :MonitoringCategory ;
                    bedreflyt:hasMonitoringCode $newCategory ;
                    bedreflyt:monitoringName "$newDescription" .
            }
            WHERE {
                bedreflyt:$oldName a :MonitoringCategory ;
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

    @CacheEvict("monitoringCategories", key = "#monitoringCategory.description")
    fun deleteCategory(monitoringCategory: MonitoringCategory) : Boolean {
        val name = monitoringCategory.description.split(" ").joinToString(" ")

        val query = """
            PREFIX bedreflyt: <$prefix>
            
            DELETE {
                bedreflyt:$name a :MonitoringCategory ;
                    bedreflyt:hasMonitoringCode ${monitoringCategory.category} ;
                    bedreflyt:monitoringName "${monitoringCategory.description}" .
            }
            WHERE {
                bedreflyt:$name a :MonitoringCategory ;
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