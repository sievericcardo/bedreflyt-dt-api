package no.uio.bedreflyt.api.config

import jakarta.annotation.PostConstruct
import no.uio.microobject.ast.expr.LiteralExpr
import no.uio.microobject.main.Settings
import no.uio.microobject.main.ReasonerMode
import no.uio.microobject.runtime.REPL
import no.uio.microobject.type.STRINGTYPE
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

@Configuration
open class REPLConfig (
    private val environmentConfig: EnvironmentConfig
) {

    private lateinit var repl: REPL

    private fun makePostRequest(url: String, headers: Map<String, String>, body: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            // Configure the connection
            connection.requestMethod = "POST"
            connection.doOutput = true
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            // Write the body
            connection.outputStream.use { outputStream ->
                outputStream.write(body.toByteArray(Charsets.UTF_8))
            }

            // Read the response
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            // Handle error response
            return connection.errorStream?.bufferedReader()?.use { it.readText() } ?: e.message.orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    private fun updateTriplestore(fusekiUrl: String): String {
        // First delete if Fuseki already contains our ontology
        val prefix = environmentConfig.getOrDefault("DOMAIN_PREFIX", "http://www.smolang.org/bedreflyt#")
        val deleteUrl = "$fusekiUrl/update"
        val deleteHeaders = mapOf("Content-Type" to "application/sparql-update")
        val deleteBody = "WITH $prefix DELETE { ?s ?p ?o } WHERE { ?s ?p ?o }"

        val deleteResponse = makePostRequest(deleteUrl, deleteHeaders, deleteBody)
        println(deleteResponse)

        // Then upload the new ontology
        val uploadUrl = "$fusekiUrl/data"
        val ontologyData = File("bedreflyt.ttl").readText()
        val uploadHeaders = mapOf("Content-Type" to "text/turtle;charset=utf-8")

        val uploadResponse = makePostRequest(uploadUrl, uploadHeaders, ontologyData)
        println(uploadResponse)

        return uploadResponse
    }

    @PostConstruct
    fun initRepl() {
        val verbose = false
        val materialize = false
        val liftedStateOutputPath = environmentConfig.getOrDefault("LIFTED_STATE_OUTPUT_PATH", "")
        val progPrefix = "https://github.com/Edkamb/SemanticObjects/Program#"
        val runPrefix = "https://github.com/Edkamb/SemanticObjects/Run" + System.currentTimeMillis() + "#"
        val langPrefix = "https://github.com/Edkamb/SemanticObjects#"
        val extraPrefixes = HashMap<String, String>()
        val useQueryType = false
        val triplestore = environmentConfig.getOrDefault("TRIPLESTORE_URL", "localhost")
        val triplestoreDataset = environmentConfig.getOrDefault("TRIPLESTORE_DATASET", "ds")
        val triplestoreUrl = "http://$triplestore:3030/$triplestoreDataset"
        val domainPrefixUri = environmentConfig.getOrDefault("DOMAIN_PREFIX_URI", "")
        val reasoner = ReasonerMode.off

        if (environmentConfig.get("EXTRA_PREFIXES") != null) {
            val prefixes = environmentConfig.get("EXTRA_PREFIXES")!!.split(";")
            for (prefix in prefixes) {
                val parts = prefix.split(",")
                extraPrefixes.putAll(mapOf(parts[0] to parts[1]))
            }
        }

        val settings = Settings(
            verbose,
            materialize,
            liftedStateOutputPath,
            triplestoreUrl,
            "",
            domainPrefixUri,
            progPrefix,
            runPrefix,
            langPrefix,
            extraPrefixes,
            useQueryType,
            reasoner
        )

        val smolPath = environmentConfig.getOrDefault("SMOL_PATH", "Bedreflyt.smol")
        repl = REPL(settings)
        repl.command("verbose", "false")
        repl.command("multiread", smolPath)
        repl.command("auto", "")
    }

    @Bean
    open fun repl(): REPL {
        return repl
    }

    @Bean
    open fun regenerateSingleModel() : (String) -> Unit = { modelName: String ->
        val escapedModelName = "\"$modelName\""
        repl.interpreter!!.tripleManager.regenerateTripleStoreModel()
        repl.interpreter!!.evalCall(
            repl.interpreter!!.getObjectNames("AssetModel")[0],
            "AssetModel",
            "reconfigureSingleModel",
            mapOf("mod" to LiteralExpr(escapedModelName, STRINGTYPE))
        )
    }
}