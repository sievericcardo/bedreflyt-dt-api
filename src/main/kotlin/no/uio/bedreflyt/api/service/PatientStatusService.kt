package no.uio.bedreflyt.api.service

import no.uio.bedreflyt.api.config.DynamicDataSourceConfig
import no.uio.bedreflyt.api.model.Patient
import no.uio.bedreflyt.api.model.PatientStatus
import no.uio.bedreflyt.api.repository.PatientStatusRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class PatientStatusService @Autowired constructor(
    private val patientStatusRepository: PatientStatusRepository,
    private val sqliteDataSource: DataSource,
    private val dynamicDataSourceConfig: DynamicDataSourceConfig
) {
    fun findAll() : MutableList<PatientStatus?> {
        return patientStatusRepository.findAll()
    }

    fun findByPatientId(patientId: Patient, sqliteDbUrl: String? = null): PatientStatus {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return patientStatusRepository.findByPatientId(patientId)
    }

    fun savePatientStatus(patientStatus: PatientStatus, sqliteDbUrl: String? = null): PatientStatus {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return patientStatusRepository.save(patientStatus)
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