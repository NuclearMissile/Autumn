package com.example.autumn.jdbc.no_tx

import com.example.autumn.annotation.*
import com.example.autumn.jdbc.JdbcTemplate
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

@ComponentScan
@Configuration
class JdbcWithoutTxApplication {
    @Bean(destroyMethod = "close")
    fun dataSource( // properties:
        @Value("\${autumn.datasource.url}") url: String?,  //
        @Value("\${autumn.datasource.username}") username: String?,  //
        @Value("\${autumn.datasource.password}") password: String?,  //
        @Value("\${autumn.datasource.driver-class-name:}") driver: String?,  //
        @Value("\${autumn.datasource.maximum-pool-size:20}") maximumPoolSize: Int,  //
        @Value("\${autumn.datasource.minimum-pool-size:1}") minimumPoolSize: Int,  //
        @Value("\${autumn.datasource.connection-timeout:30000}") connTimeout: Int //
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
}

data class Address(
    var id: Int = 0,
    var userId: Int = 0,
    var address: String? = null,
    var zipcode: Int = 0
) {
    fun setZip(zip: Int?) {
        this.zipcode = zip ?: 0
    }
}

data class User(var id: Int = 0, var name: String? = null, var age: Int? = null)
