package org.example.autumn.db.orm

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.example.autumn.annotation.*
import org.example.autumn.aop.AroundProxyBeanPostProcessor
import org.example.autumn.db.DataSourceTransactionManager
import org.example.autumn.db.JdbcTemplate
import org.example.autumn.db.TransactionManager
import org.example.autumn.db.TransactionalBeanPostProcessor
import javax.sql.DataSource

@Configuration
class OrmTestConfiguration {
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


@Configuration
class AroundAopConfiguration {
    @Bean
    fun aroundProxyBeanPostProcessor(): AroundProxyBeanPostProcessor {
        return AroundProxyBeanPostProcessor()
    }
}