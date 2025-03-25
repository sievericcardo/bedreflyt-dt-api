package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.Task
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
class TaskService (
    replConfig: REPLConfig,
    triplestoreProperties: TriplestoreProperties
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    @CachePut("tasks", key = "#taskName")
    fun createTask(taskName: String) : Boolean {
        val name = taskName.replace(" ", "")
        val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            INSERT DATA {
                bedreflyt:$name rdf:type owl:NamedIndividual , 
                        <http://purl.org/net/p-plan#Step> ;
                    bedreflyt:taskName "$taskName" .
            }
        """

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

    @Cacheable("tasks")
    fun getAllTasks() : List<Task>? {
        val tasks: MutableList<Task> = mutableListOf()

        val query =
            """
           SELECT DISTINCT ?taskName ?averageDuration ?bedCategory WHERE {
            ?obj a prog:Task ;
                prog:Task_taskName ?taskName .
        }"""

        val resultTasks: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultTasks.hasNext()) {
            return null
        }

        while (resultTasks.hasNext()) {
            val solution: QuerySolution = resultTasks.next()
            val taskName = solution.get("?taskName").asLiteral().toString()
            tasks.add(Task(taskName))
        }

        return tasks
    }

    @Cacheable("tasks", key = "#taskName")
    fun getTaskByTaskName(taskName: String) : Task? {
        val query = """
            SELECT DISTINCT ?taskName WHERE {
                ?obj a prog:Task ;
                    prog:Task_taskName ?taskName .
                FILTER (?taskName = "$taskName")
            }
        """

        val resultTask: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultTask.hasNext()) {
            return null
        }

        val solution: QuerySolution = resultTask.next()

        return Task(taskName)
    }

    @CacheEvict("tasks", key = "#task.taskName")
    @CachePut("tasks", key = "#newTaskName")
    fun updateTask(task: Task, newTaskName: String) : Boolean {
        val oldName = task.taskName.replace(" ", "")
        val newName = newTaskName.replace(" ", "")
        val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            DELETE {
                bedreflyt:$oldName rdf:type owl:NamedIndividual , 
                        <http://purl.org/net/p-plan#Step> ;
                    bedreflyt:taskName "${task.taskName}" .
            }
            INSERT {
                bedreflyt:$newName rdf:type owl:NamedIndividual , 
                        <http://purl.org/net/p-plan#Step> ;
                    bedreflyt:taskName "$newTaskName" .
            }
            WHERE {
               bedreflyt:$oldName rdf:type owl:NamedIndividual , 
                        <http://purl.org/net/p-plan#Step> ;
                    bedreflyt:taskName "${task.taskName}" .
            }
        """

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

    @CacheEvict("tasks", key = "#task.taskName")
    fun deleteTask(task: Task) : Boolean {
        val name = task.taskName.replace(" ", "")
        val query = """
            PREFIX bedreflyt: <$prefix>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            DELETE {
                bedreflyt:$name rdf:type owl:NamedIndividual , 
                        <http://purl.org/net/p-plan#Step> ;
                    bedreflyt:taskName "${task.taskName}" .
            }
            WHERE {
                bedreflyt:$name rdf:type owl:NamedIndividual , 
                        <http://purl.org/net/p-plan#Step> ;
                    bedreflyt:taskName "${task.taskName}" .
            }
        """

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