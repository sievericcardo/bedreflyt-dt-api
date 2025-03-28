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
}