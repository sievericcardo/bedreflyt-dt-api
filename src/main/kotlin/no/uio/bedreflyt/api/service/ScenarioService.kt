package no.uio.bedreflyt.api.service

import no.uio.bedreflyt.api.config.DynamicDataSourceConfig
import no.uio.bedreflyt.api.model.Patient
import no.uio.bedreflyt.api.model.Scenario
import no.uio.bedreflyt.api.repository.ScenarioRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class ScenarioService @Autowired constructor(
    private val scenarioRepository: ScenarioRepository,
    private val sqliteDataSource: DataSource,
    private val dynamicDataSourceConfig: DynamicDataSourceConfig
) {
    fun findAll(): MutableList<Scenario?> {
        return scenarioRepository.findAll()
    }

    fun findByBatch (batch: Int, sqliteDbUrl: String? = null): Scenario {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return scenarioRepository.findByBatch(batch)
    }

    fun findByPatientId(patientId: Patient, sqliteDbUrl: String? = null): Scenario {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return scenarioRepository.findByPatientId(patientId)
    }

    fun findByTreatmentName(treatmentName: String, sqliteDbUrl: String? = null): Scenario {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return scenarioRepository.findByTreatmentName(treatmentName)
    }

    fun saveScenario(scenario: Scenario, sqliteDbUrl: String? = null): Scenario {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return scenarioRepository.save(scenario)
    }

    private fun configureEntityManagerFactory(dataSource: DataSource) {
        val emFactory = LocalContainerEntityManagerFactoryBean()
        emFactory.dataSource = dataSource
        emFactory.setPackagesToScan("no.uio.bedreflyt.api.model")
        emFactory.jpaVendorAdapter = HibernateJpaVendorAdapter().apply {
            setDatabasePlatform("org.hibernate.community.dialect.SQLiteDialect")
        }
        emFactory.afterPropertiesSet()
        val transactionManager = JpaTransactionManager()
        transactionManager.entityManagerFactory = emFactory.getObject()
    }
}