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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantReadWriteLock

@Service
open class TaskService (
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

    @CachePut("tasks", key = "#taskName")
    open fun createTask(taskName: String) : Task? {
        lock.writeLock().lock()
        try {
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
                replConfig.regenerateSingleModel().invoke("tasks")
                return Task(taskName)
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @Cacheable("tasks")
    open fun getAllTasks() : List<Task>? {
        lock.readLock().lock()
        try {
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
        } finally {
            lock.readLock().unlock()
        }
    }

    @Cacheable("tasks", key = "#taskName")
    open fun getTaskByTaskName(taskName: String) : Task? {
        lock.readLock().lock()
        try {
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
        } finally {
            lock.readLock().unlock()
        }
    }

    @CacheEvict("tasks", key = "#task.taskName")
    @CachePut("tasks", key = "#newTaskName")
    open fun updateTask(task: Task, newTaskName: String) : Task? {
        lock.writeLock().lock()
        try {
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
                replConfig.regenerateSingleModel().invoke("tasks")
                return Task(newTaskName)
            } catch (_: Exception) {
                return null
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @CacheEvict("tasks", allEntries = true)
    open fun deleteTask(task: Task) : Boolean {
        lock.writeLock().lock()
        try {
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
                replConfig.regenerateSingleModel().invoke("tasks")
                return true
            } catch (_: Exception) {
                return false
            }
        } finally {
            lock.writeLock().unlock()
        }
    }
}