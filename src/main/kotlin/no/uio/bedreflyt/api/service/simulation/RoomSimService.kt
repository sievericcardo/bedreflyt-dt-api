package no.uio.bedreflyt.api.service.simulation

import no.uio.bedreflyt.api.config.DynamicDataSourceConfig
import no.uio.bedreflyt.api.model.simulation.RoomSim
import no.uio.bedreflyt.api.repository.simulation.RoomSimRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class RoomSimService @Autowired constructor(
    private val roomSimRepository: RoomSimRepository,
    private val sqliteDataSource: DataSource,
    private val dynamicDataSourceConfig: DynamicDataSourceConfig
) {
    fun findAll() : MutableList<RoomSim?> {
        return roomSimRepository.findAll()
    }

    fun findByRoomDescription(roomDescription: String, sqliteDbUrl: String? = null): RoomSim {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return roomSimRepository.findByRoomDescription(roomDescription)
    }

    fun saveRoom(room: RoomSim, sqliteDbUrl: String? = null): RoomSim {
        if (sqliteDbUrl != null) {
            dynamicDataSourceConfig.setSqliteDatabaseUrl(sqliteDataSource, sqliteDbUrl)
            configureEntityManagerFactory(sqliteDataSource)
        }
        return roomSimRepository.save(room)
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