package no.uio.bedreflyt.api.service.simulation

import no.uio.bedreflyt.api.config.DatabaseContextHolder
import no.uio.bedreflyt.api.config.DynamicDataSourceConfig
import no.uio.bedreflyt.api.model.simulation.PatientSim
import no.uio.bedreflyt.api.repository.simulation.PatientSimRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class PatientSimService @Autowired constructor(
    private val patientSimRepository: PatientSimRepository,
    private val sqliteDataSource: DataSource,
    private val dynamicDataSourceConfig: DynamicDataSourceConfig
) {
    fun findAll(): MutableList<PatientSim?> {
        return patientSimRepository.findAll()
    }

    fun findByPatientId(patientId: String, sqliteDbUrl: String? = null): PatientSim {
        if (sqliteDbUrl != null) {
            DatabaseContextHolder.setDatabaseType("sqlite")
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        } else {
            DatabaseContextHolder.setDatabaseType("postgres")
        }
        return patientSimRepository.findByPatientId(patientId)
    }

    fun savePatient(patient: PatientSim, sqliteDbUrl: String? = null): PatientSim {
        if (sqliteDbUrl != null) {
            DatabaseContextHolder.setDatabaseType("sqlite")
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        } else {
            DatabaseContextHolder.setDatabaseType("postgres")
        }
        return patientSimRepository.save(patient)
    }

    private fun configureEntityManagerFactory(dataSource: DataSource) {
        val emFactory = LocalContainerEntityManagerFactoryBean()
        emFactory.dataSource = dataSource
        emFactory.setPackagesToScan("no.uio.bedreflyt.api.model")
        emFactory.jpaVendorAdapter = HibernateJpaVendorAdapter().apply {
            setDatabasePlatform("org.hibernate.dialect.SQLiteDialect")
        }
        emFactory.afterPropertiesSet()
        val transactionManager = JpaTransactionManager()
        transactionManager.entityManagerFactory = emFactory.getObject()
    }
}