//package no.uio.bedreflyt.api.config
//
//import org.springframework.boot.context.properties.ConfigurationProperties
//import org.springframework.boot.jdbc.DataSourceBuilder
//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Configuration
//import org.springframework.data.jpa.repository.config.EnableJpaRepositories
//import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
//import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
//import org.springframework.beans.factory.annotation.Qualifier
//import org.springframework.context.annotation.Primary
//import org.springframework.orm.jpa.JpaTransactionManager
//import javax.sql.DataSource
//
//@Configuration
//@EnableJpaRepositories(
//    basePackages = ["no.uio.bedreflyt.api.repository.live"],
//    entityManagerFactoryRef = "liveEntityManagerFactory",
//    transactionManagerRef = "liveTransactionManager"
//)
//open class LiveDataSourceConfig {
//
//    @Bean
//    @Primary
//    @ConfigurationProperties(prefix = "spring.datasource")
//    open fun liveDataSource(): DataSource {
//        return DataSourceBuilder.create().build()
//    }
//
//    @Bean
//    @Primary
//    open fun liveEntityManagerFactory(
//        @Qualifier("liveDataSource") dataSource: DataSource
//    ): LocalContainerEntityManagerFactoryBean {
//        val emFactory = LocalContainerEntityManagerFactoryBean()
//        emFactory.dataSource = dataSource
//        emFactory.setPackagesToScan("no.uio.bedreflyt.api.model.live")
//        emFactory.jpaVendorAdapter = HibernateJpaVendorAdapter().apply {
//            setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect")
//        }
//        return emFactory
//    }
//
//    @Bean
//    open fun liveTransactionManager(
//        @Qualifier("liveEntityManagerFactory") liveEntityManagerFactory: LocalContainerEntityManagerFactoryBean
//    ): JpaTransactionManager {
//        return JpaTransactionManager().apply {
//            entityManagerFactory = liveEntityManagerFactory.getObject()
//        }
//    }
//}
//
//@Configuration
//@EnableJpaRepositories(
//    basePackages = ["no.uio.bedreflyt.api.repository.simulation"],
//    entityManagerFactoryRef = "simulationEntityManagerFactory",
//    transactionManagerRef = "simulationTransactionManager"
//)
//open class SimulationDataSourceConfig {
//
//    @Bean
//    @ConfigurationProperties(prefix = "spring.second-datasource")
//    open fun simulationDataSource(): DataSource {
//        return DataSourceBuilder.create().build()
//    }
//
//    @Bean
//    open fun simulationEntityManagerFactory(
//        @Qualifier("simulationDataSource") dataSource: DataSource
//    ): LocalContainerEntityManagerFactoryBean {
//        val emFactory = LocalContainerEntityManagerFactoryBean()
//        emFactory.dataSource = dataSource
//        emFactory.setPackagesToScan("no.uio.bedreflyt.api.model.simulation")
//        emFactory.jpaVendorAdapter = HibernateJpaVendorAdapter().apply {
//            setDatabasePlatform("org.hibernate.community.dialect.SQLiteDialect")
//        }
//        return emFactory
//    }
//
//    @Bean
//    open fun simulationTransactionManager(
//        @Qualifier("simulationEntityManagerFactory") simulationEntityManagerFactory: LocalContainerEntityManagerFactoryBean
//    ): JpaTransactionManager {
//        return JpaTransactionManager().apply {
//            entityManagerFactory = simulationEntityManagerFactory.getObject()
//        }
//    }
//}