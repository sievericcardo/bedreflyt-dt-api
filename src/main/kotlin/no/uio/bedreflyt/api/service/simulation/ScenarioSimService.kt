package no.uio.bedreflyt.api.service.simulation

import no.uio.bedreflyt.api.config.DynamicDataSourceConfig
import no.uio.bedreflyt.api.model.simulation.PatientSim
import no.uio.bedreflyt.api.model.simulation.ScenarioSim
import no.uio.bedreflyt.api.repository.simulation.ScenarioSimRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class ScenarioSimService @Autowired constructor(
    private val scenarioSimRepository: ScenarioSimRepository,
    private val sqliteDataSource: DataSource,
    private val dynamicDataSourceConfig: DynamicDataSourceConfig
) {
    fun findAll(): MutableList<ScenarioSim?> {
        return scenarioSimRepository.findAll()
    }

    fun findByBatch (batch: Int, sqliteDbUrl: String? = null): ScenarioSim {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return scenarioSimRepository.findByBatch(batch)
    }

    fun findByPatientId(patientId: PatientSim, sqliteDbUrl: String? = null): ScenarioSim {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return scenarioSimRepository.findByPatientId(patientId)
    }

    fun findByTreatmentName(treatmentName: String, sqliteDbUrl: String? = null): ScenarioSim {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return scenarioSimRepository.findByTreatmentName(treatmentName)
    }

    fun saveScenario(scenarioSim: ScenarioSim, sqliteDbUrl: String? = null): ScenarioSim {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return scenarioSimRepository.save(scenarioSim)
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