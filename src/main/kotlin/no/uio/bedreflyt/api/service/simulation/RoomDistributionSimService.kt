package no.uio.bedreflyt.api.service.simulation

import no.uio.bedreflyt.api.config.DynamicDataSourceConfig
import no.uio.bedreflyt.api.model.simulation.RoomDistributionSim
import no.uio.bedreflyt.api.repository.simulation.RoomDistributionSimRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class RoomDistributionSimService @Autowired constructor(
    private val roomDistributionSimRepository: RoomDistributionSimRepository,
    private val sqliteDataSource: DataSource,
    private val dynamicDataSourceConfig: DynamicDataSourceConfig
) {
    fun findAll(): MutableList<RoomDistributionSim?> {
        return roomDistributionSimRepository.findAll()
    }

    fun findByRoom_RoomDescription(roomDescription: String, sqliteDbUrl: String? = null): List<RoomDistributionSim> {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return roomDistributionSimRepository.findByRoom_RoomDescription(roomDescription)
    }

    fun saveRoomDistribution(roomDistributionSim: RoomDistributionSim, sqliteDbUrl: String? = null): RoomDistributionSim {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return roomDistributionSimRepository.save(roomDistributionSim)
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