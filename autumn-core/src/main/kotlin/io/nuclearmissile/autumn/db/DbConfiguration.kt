package io.nuclearmissile.autumn.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.nuclearmissile.autumn.DEFAULT_TX_MANAGER_ORDER
import io.nuclearmissile.autumn.annotation.*
import io.nuclearmissile.autumn.context.ApplicationContextHolder
import io.nuclearmissile.autumn.db.orm.EntityMapper
import io.nuclearmissile.autumn.db.orm.NaiveOrm
import jakarta.persistence.Entity
import javax.sql.DataSource

@Configuration
class DbConfiguration {
    @Bean(destroyMethod = "close")
    fun dataSource(
        @Value("autumn.datasource.url") url: String,
        @Value("autumn.datasource.username") username: String,
        @Value("autumn.datasource.password") password: String,
        @Value("autumn.datasource.driver-class-name") driver: String,
        @Value("autumn.datasource.maximum-pool-size") maximumPoolSize: Int,
        @Value("autumn.datasource.minimum-pool-size") minimumPoolSize: Int,
        @Value("autumn.datasource.connection-timeout") connTimeout: Int,
    ): DataSource {
        return HikariDataSource(HikariConfig().also { config ->
            config.isAutoCommit = false
            config.jdbcUrl = url
            config.username = username
            config.password = password
            config.driverClassName = driver
            config.maximumPoolSize = maximumPoolSize
            config.minimumIdle = minimumPoolSize
            config.connectionTimeout = connTimeout.toLong()
        })
    }

    @Bean
    fun jdbcTemplate(@Autowired dataSource: DataSource): JdbcTemplate {
        return JdbcTemplate(dataSource)
    }

    @Bean
    fun naiveOrm(@Autowired jdbcTemplate: JdbcTemplate): NaiveOrm {
        val classMapping = ApplicationContextHolder.required.managedClassNames
            .map { Class.forName(it) }
            .filter { it.isAnnotationPresent(Entity::class.java) }
            .associateWith { EntityMapper(it) }
        return NaiveOrm(jdbcTemplate, classMapping)
    }

    @Bean
    fun transactionalBeanPostProcessor(): TransactionalBeanPostProcessor {
        return TransactionalBeanPostProcessor()
    }

    @Order(DEFAULT_TX_MANAGER_ORDER)
    @Bean
    fun transactionManager(@Autowired dataSource: DataSource): TransactionManager {
        return DataSourceTransactionManager(dataSource)
    }
}