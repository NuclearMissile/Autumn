package com.example.autumn.jdbc

import com.example.autumn.annotation.Autowired
import com.example.autumn.annotation.Bean
import com.example.autumn.annotation.Configuration
import com.example.autumn.annotation.Value
import com.example.autumn.jdbc.tx.DataSourceTransactionManager
import com.example.autumn.jdbc.tx.TransactionManager
import com.example.autumn.jdbc.tx.TransactionalBeanPostProcessor
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

@Configuration
class JdbcConfiguration {
    @Bean(destroyMethod = "close")
    fun dataSource(
        @Value("\${autumn.datasource.url}") url: String,
        @Value("\${autumn.datasource.username}") username: String,
        @Value("\${autumn.datasource.password}") password: String,
        @Value("\${autumn.datasource.driver-class-name:}") driver: String,
        @Value("\${autumn.datasource.maximum-pool-size:20}") maximumPoolSize: Int,
        @Value("\${autumn.datasource.minimum-pool-size:1}") minimumPoolSize: Int,
        @Value("\${autumn.datasource.connection-timeout:30000}") connTimeout: Int
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
    fun transactionalBeanPostProcessor(): TransactionalBeanPostProcessor {
        return TransactionalBeanPostProcessor()
    }

    @Bean
    fun transactionManager(@Autowired dataSource: DataSource): TransactionManager {
        return DataSourceTransactionManager(dataSource)
    }
}