package no.uio.bedreflyt.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class TriplestoreConfig {

    @Bean
    open fun triplestoreProperties(): TriplestoreProperties {
        val host = System.getenv().getOrDefault("TRIPLESTORE_URL", "localhost")
        val dataStore = System.getenv().getOrDefault("TRIPLESTORE_DATASET", "Bedreflyt")
        val tripleStore = "http://$host:3030/$dataStore"
        val prefix = System.getenv().getOrDefault("DOMAIN_PREFIX", "http://www.smolang.org/bedreflyt#")
        val ttlPrefix = if (prefix.isNotEmpty()) prefix.dropLast(1) else prefix
        return TriplestoreProperties(tripleStore, prefix, ttlPrefix)
    }
}

data class TriplestoreProperties(
    val tripleStore: String,
    val prefix: String,
    val ttlPrefix: String
)