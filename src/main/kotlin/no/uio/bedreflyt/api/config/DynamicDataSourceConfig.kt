package no.uio.bedreflyt.api.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import javax.sql.DataSource

@Configuration
open class DynamicDataSourceConfig {

    @Value("\${spring.datasource.url}")
    private lateinit var postgresUrl: String

    @Value("\${spring.datasource.driver-class-name}")
    private lateinit var postgresDriverClassName: String

    @Value("\${spring.datasource.username}")
    private lateinit var postgresUsername: String

    @Value("\${spring.datasource.password}")
    private lateinit var postgresPassword: String

    @Value("\${spring.second-datasource.url}")
    private lateinit var sqliteUrl: String

    @Value("\${spring.second-datasource.driver-class-name}")
    private lateinit var sqliteDriverClassName: String

    @Bean
    @Primary
    @Qualifier("postgresDataSource")
    open fun postgresDataSource(): DataSource {
        return DriverManagerDataSource().apply {
            setDriverClassName(postgresDriverClassName)
            url = postgresUrl
            username = postgresUsername
            password = postgresPassword
        }
    }

    @Bean
    @Qualifier("sqliteDataSource")
    open fun sqliteDataSource(): DataSource {
        return DriverManagerDataSource().apply {
            setDriverClassName(sqliteDriverClassName)
            url = sqliteUrl
        }
    }

    open fun setSqliteDatabaseUrl(@Qualifier("sqliteDataSource") dataSource: DataSource, url: String = sqliteUrl) {
        if (dataSource is DriverManagerDataSource) {
            dataSource.url = url
        }
    }

    @Bean
    open fun entityManagerFactory(dataSource: DataSource): LocalContainerEntityManagerFactoryBean {
        val emFactory = LocalContainerEntityManagerFactoryBean()
        emFactory.dataSource = dataSource
        emFactory.setPackagesToScan("no.uio.bedreflyt.api.model")
        emFactory.jpaVendorAdapter = HibernateJpaVendorAdapter().apply {
            setDatabasePlatform("org.hibernate.community.dialect.SQLiteDialect")
        }
        return emFactory
    }
}