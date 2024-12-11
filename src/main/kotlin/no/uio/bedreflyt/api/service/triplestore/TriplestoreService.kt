package no.uio.bedreflyt.api.service.triplestore

import no.uio.bedreflyt.api.config.REPLConfig
import no.uio.bedreflyt.api.config.TriplestoreProperties
import no.uio.bedreflyt.api.model.triplestore.*
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import org.apache.jena.update.UpdateRequest
import org.springframework.stereotype.Service
import java.io.File

@Service
class TriplestoreService (
    private val replConfig: REPLConfig,
    private val triplestoreProperties: TriplestoreProperties
) {

    private val tripleStore = triplestoreProperties.tripleStore
    private val prefix = triplestoreProperties.prefix
    private val ttlPrefix = triplestoreProperties.ttlPrefix
    private val repl = replConfig.repl()

    fun replaceContentIgnoringSpaces(filePath: String, oldContent: String, newContent: String) {
        // Read the file content
        val file = File(filePath)
        if (!file.exists()) {
            println("File does not exist: $filePath")
            return
        }

        val fileContent = file.readText(Charsets.UTF_8)

        // Normalize whitespace by trimming leading and trailing spaces and removing extra indentations
        val normalizedContent = fileContent.trimIndent()

        // Normalize the old and new strings as well
        val normalizedNewContent = newContent.trimIndent()

        val oldContentRegex = Regex(
            oldContent.lines().joinToString("\n") {
                "\\s*${Regex.escape(it.trim())}"
            }
        )

        val updatedContent = normalizedContent.replace(oldContentRegex, normalizedNewContent)

        // Write the updated content back to the file
        file.writeText(updatedContent)
        println("Content replaced successfully in file: $filePath")
    }

    /*
     * Create operations
     */
    fun createTreatment(diagnosis: String, journeyOrder: Int, task: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            INSERT DATA {
                :journeyStep${journeyOrder}_$diagnosis a :JourneyStep ;
                    :diagnosis "$diagnosis" ;
                    :journeyOrder $journeyOrder ;
                    :task "$task" .
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

    /*
     * Read operations
     */
    fun getAllTreatments(): List<JourneyStep>? {
        val treatments: MutableList<JourneyStep> = mutableListOf()

        val query = """
        SELECT DISTINCT ?diagnosis ?journeyOrder ?task WHERE {
            ?obj a prog:JourneyStep ;
                prog:JourneyStep_diagnosis ?diagnosis ;
                prog:JourneyStep_journeyOrder ?journeyOrder ;
                prog:JourneyStep_task ?task .
        }"""

        val resultTreatments: ResultSet = repl.interpreter!!.query(query)!!

        if (!resultTreatments.hasNext()) {
            return null
        }

        while (resultTreatments.hasNext()) {
            val solution: QuerySolution = resultTreatments.next()
            val diagnosis = solution.get("?diagnosis").asLiteral().toString()
            val journeyOrder = solution.get("?journeyOrder").asLiteral().toString().split("^^")[0].toInt()
            val task = solution.get("?task").asLiteral().toString()
            treatments.add(JourneyStep(diagnosis, journeyOrder, task))
        }

        return treatments
    }

    /*
     * Update operations
     */
    fun updateTreatment(oldDiagnosis: String, oldJourneyOrder: Int, oldTask: String, newDiagnosis: String, newJourneyOrder: Int, newTask: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :journeyStep${oldJourneyOrder}_$oldDiagnosis :diagnosis "$oldDiagnosis" ;
                    :journeyOrder $oldJourneyOrder ;
                    :task "$oldTask" .
            }
            INSERT {
                :journeyStep${newJourneyOrder}_$newDiagnosis a :JourneyStep ;
                    :diagnosis "$newDiagnosis" ;
                    :journeyOrder $newJourneyOrder ;
                    :task "$newTask" .
            }
            WHERE {
                :journeyStep${oldJourneyOrder}_$oldDiagnosis a :JourneyStep ;
                    :diagnosis "$oldDiagnosis" ;
                    :journeyOrder $oldJourneyOrder ;
                    :task "$oldTask" .
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

    /*
     * Delete operations
     */
    fun deleteTreatment(diagnosis: String, journeyOrder: Int, task: String) : Boolean {
        val query = """
            PREFIX : <$prefix>
            
            DELETE {
                :journeyStep${journeyOrder}_$diagnosis a :JourneyStep ;
                    :diagnosis "$diagnosis" ;
                    :journeyOrder $journeyOrder ;
                    :task "$task" .
            }
            WHERE {
                :journeyStep${journeyOrder}_$diagnosis a :JourneyStep ;
                    :diagnosis "$diagnosis" ;
                    :journeyOrder $journeyOrder ;
                    :task "$task" .
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