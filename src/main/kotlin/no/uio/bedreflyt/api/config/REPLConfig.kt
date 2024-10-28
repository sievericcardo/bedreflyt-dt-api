package no.uio.bedreflyt.api.config

import no.uio.microobject.main.Settings
import no.uio.microobject.main.ReasonerMode
import no.uio.microobject.runtime.REPL
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class REPLConfig {

    @Bean
    open fun settings(): Settings {
        val verbose = true
        val materialize = false
        val liftedStateOutputPath = System.getenv("LIFTED_STATE_OUTPUT_PATH") ?: ""
        val progPrefix = "https://github.com/Edkamb/SemanticObjects/Program#"
        val runPrefix = "https://github.com/Edkamb/SemanticObjects/Run" + System.currentTimeMillis() + "#"
        val langPrefix = "https://github.com/Edkamb/SemanticObjects#"
        val extraPrefixes = HashMap<String, String>()
        val useQueryType = false
        val triplestoreUrl = System.getenv("TRIPLESTORE_URL") ?: "http://localhost:3030/ds"
        val domainPrefixUri = System.getenv("DOMAIN_PREFIX_URI") ?: ""
        val reasoner = ReasonerMode.off

        return Settings(
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
    }

    @Bean
    open fun repl(settings: Settings): REPL {
        val smolPath = System.getenv("SMOL_PATH") ?: "Bedreflyt.smol"
        val repl = REPL(settings)
        repl.command("verbose", "true")
        repl.command("reada", smolPath)
        return repl
    }
}