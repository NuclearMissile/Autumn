package org.example.autumn.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.example.autumn.annotation.Autowired
import org.example.autumn.annotation.Bean
import org.example.autumn.annotation.Configuration
import org.example.autumn.annotation.Value
import org.example.autumn.db.orm.NaiveOrm
import javax.sql.DataSource

@Configuration
class DbConfiguration {
    @Bean(destroyMethod = "close")
    fun dataSource(
        @Value("\${autumn.datasource.url}") url: String,
        @Value("\${autumn.datasource.username}") username: String,
        @Value("\${autumn.datasource.password}") password: String,
        @Value("\${autumn.datasource.driver-class-name:}") driver: String,
        @Value("\${autumn.datasource.maximum-pool-size:20}") maximumPoolSize: Int,
        @Value("\${autumn.datasource.minimum-pool-size:1}") minimumPoolSize: Int,
        @Value("\${autumn.datasource.connection-timeout:30000}") connTimeout: Int,
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
        return NaiveOrm(jdbcTemplate)
    }

    @Bean
    fun transactionalBeanPostProcessor(): TransactionalBeanPostProcessor {
        return TransactionalBeanPostProcessor()
    }

    @Bean
    fun transactionManager(@Autowired dataSource: DataSource): TransactionManager {
        return DataSourceTransactionManager(dataSource)
    }
}