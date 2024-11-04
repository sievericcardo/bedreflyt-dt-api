package no.uio.bedreflyt.api.service.simulation

import no.uio.bedreflyt.api.config.DynamicDataSourceConfig
import no.uio.bedreflyt.api.model.simulation.PatientSim
import no.uio.bedreflyt.api.model.simulation.PatientStatusSim
import no.uio.bedreflyt.api.repository.simulation.PatientStatusSimRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class PatientStatusSimService @Autowired constructor(
    private val patientStatusSimRepository: PatientStatusSimRepository,
    private val sqliteDataSource: DataSource,
    private val dynamicDataSourceConfig: DynamicDataSourceConfig
) {
    fun findAll() : MutableList<PatientStatusSim?> {
        return patientStatusSimRepository.findAll()
    }

    fun findByPatientId(patientId: PatientSim, sqliteDbUrl: String? = null): PatientStatusSim {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return patientStatusSimRepository.findByPatientId(patientId)
    }

    fun savePatientStatus(patientStatus: PatientStatusSim, sqliteDbUrl: String? = null): PatientStatusSim {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return patientStatusSimRepository.save(patientStatus)
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