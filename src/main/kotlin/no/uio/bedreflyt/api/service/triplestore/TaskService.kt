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
        val query = """
            PREFIX bedreflyt: <$prefix>
            
            INSERT DATA {
                bedreflyt:task_$taskName rdf:type owl:NamedIndividual , 
                        <http://purl.org/net/p-plan#Step> ;
                    :taskName "$taskName" .
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
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :task_${task.taskName} rdf:type owl:NamedIndividual , 
                        <http://purl.org/net/p-plan#Step> ;
                    :taskName "${task.taskName}" .
            }
            INSERT {
                :task_${task.taskName} rdf:type owl:NamedIndividual , 
                        <http://purl.org/net/p-plan#Step> ;
                    :bed $newTaskName .
            }
            WHERE {
               :task_${task.taskName} rdf:type owl:NamedIndividual , 
                        <http://purl.org/net/p-plan#Step> ;
                    :taskName "${task.taskName}" .
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
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :task_${task.taskName} rdf:type owl:NamedIndividual , 
                        <http://purl.org/net/p-plan#Step> ;
                    :taskName "${task.taskName}" .
            }
            WHERE {
                :task_${task.taskName} rdf:type owl:NamedIndividual , 
                        <http://purl.org/net/p-plan#Step> ;
                    :taskName "${task.taskName}" .
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