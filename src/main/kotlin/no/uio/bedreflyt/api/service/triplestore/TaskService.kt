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

class TaskService (
    private val replConfig: REPLConfig,
    private val triplestoreProperties: TriplestoreProperties
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    fun createTask(taskName: String, averageDuration: Double, bed: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :task_$taskName a :Task ;
                    :taskName "$taskName" ;
                    :averageDuration $averageDuration ;
                    :bed $bed .
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

    fun getAllTasks() : List<Task>? {
        val tasks: MutableList<Task> = mutableListOf()

        val query =
            """
           SELECT DISTINCT ?taskName ?averageDuration ?bedCategory WHERE {
            ?obj a prog:Task ;
                prog:Task_taskName ?taskName ;
                prog:Task_durationAverage ?averageDuration ;
                prog:Task_bed ?bedCategory .
        }"""

        val resultTasks: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultTasks.hasNext()) {
            return null
        }

        while (resultTasks.hasNext()) {
            val solution: QuerySolution = resultTasks.next()
            val taskName = solution.get("?taskName").asLiteral().toString()
            val averageDuration = solution.get("?averageDuration").asLiteral().toString().split("^^")[0].toDouble()
            val bedCategory = solution.get("?bedCategory").asLiteral().toString().split("^^")[0].toInt()
            tasks.add(Task(taskName, averageDuration, bedCategory))
        }

        return tasks
    }

    fun getTaskByTaskName(taskName: String) : Task? {
        val query = """
            PREFIX : <$prefix>
            
            SELECT DISTINCT ?taskName ?averageDuration ?bed WHERE {
                ?obj a prog:Task ;
                    prog:Task_taskName "$taskName" ;
                    prog:Task_durationAverage ?averageDuration ;
                    prog:Task_bed ?bed .
            }
        """

        val resultTask: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultTask.hasNext()) {
            return null
        }

        val solution: QuerySolution = resultTask.next()
        val taskName = solution.get("?taskName").asLiteral().toString()
        val averageDuration = solution.get("?averageDuration").asLiteral().toString().split("^^")[0].toDouble()
        val bed = solution.get("?bed").asLiteral().toString().split("^^")[0].toInt()

        return Task(taskName, averageDuration, bed)
    }

    fun updateTask(oldTaskName: String, oldAverageDuration: Double, oldBed: Int, newTaskName: String, newAverageDuration: Double, newBed: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :task_$oldTaskName a :Task ;
                    :taskName "$oldTaskName" ;
                    :averageDuration $oldAverageDuration ;
                    :bed $oldBed .
            }
            INSERT {
                :task_$newTaskName a :Task ;
                    :taskName "$newTaskName" ;
                    :averageDuration $newAverageDuration ;
                    :bed $newBed .
            }
            WHERE {
               :task_$oldTaskName a :Task ;
                    :taskName "$oldTaskName" ;
                    :averageDuration $oldAverageDuration ;
                    :bed $oldBed .
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

    fun deleteTask(taskName: String, averageDuration: Double, bed: Int) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :task_$taskName a :Task ;
                    :taskName "$taskName" ;
                    :averageDuration $averageDuration ;
                    :bed $bed .
            }
            WHERE {
                :task_$taskName a :Task ;
                    :taskName "$taskName" ;
                    :averageDuration $averageDuration ;
                    :bed $bed .
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