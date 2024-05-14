package org.example.autumn.db.no_tx

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.example.autumn.annotation.*
import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.db.JdbcTemplate
import org.example.autumn.db.JdbcTestBase
import org.example.autumn.db.User
import org.example.autumn.exception.DataAccessException
import org.junit.jupiter.api.assertThrows
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ComponentScan
@Configuration
class JdbcNoTxConfiguration {
    @Bean(destroyMethod = "close")
    fun dataSource( // properties:
        @Value("\${autumn.datasource.url}") url: String?,
        @Value("\${autumn.datasource.username}") username: String?,
        @Value("\${autumn.datasource.password}") password: String?,
        @Value("\${autumn.datasource.driver-class-name:}") driver: String?,
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
}

class JdbcNoTxTest : JdbcTestBase() {
    @Test
    fun testJdbcNoTx() {
        AnnotationConfigApplicationContext(JdbcNoTxConfiguration::class.java, config).use { ctx ->
            val jdbcTemplate = ctx.getBean(JdbcTemplate::class.java)
            jdbcTemplate.update(CREATE_USER)
            jdbcTemplate.update(CREATE_ADDRESS)
            // insert user:
            val userId1 = jdbcTemplate.updateWithGeneratedKey(INSERT_USER, "Bob", 12).toInt()
            val userId2 = jdbcTemplate.updateWithGeneratedKey(INSERT_USER, "Alice", null).toInt()
            assertEquals(1, userId1)
            assertEquals(2, userId2)
            // query user:
            val bob = jdbcTemplate.queryRequiredObject(SELECT_USER, User::class.java, userId1)
            val alice = jdbcTemplate.queryRequiredObject(SELECT_USER, User::class.java, userId2)
            assertEquals(1, bob.id)
            assertEquals("Bob", bob.name)
            assertEquals(12, bob.age)
            assertEquals(2, alice.id)
            assertEquals("Alice", alice.name)
            assertNull(alice.age)
            // query name:
            assertEquals("Bob", jdbcTemplate.queryRequiredObject(SELECT_USER_NAME, String::class.java, userId1))
            assertEquals(12, jdbcTemplate.queryRequiredObject(SELECT_USER_AGE, Int::class.java, userId1))
            // update user:
            val n1 = jdbcTemplate.update(UPDATE_USER, "Bob Jones", 18, bob.id)
            assertEquals(1, n1)
            // delete user:
            val n2 = jdbcTemplate.update(DELETE_USER, alice.id)
            assertEquals(1, n2)
        }

        AnnotationConfigApplicationContext(JdbcNoTxConfiguration::class.java, config).use { ctx ->
            val jdbcTemplate = ctx.getBean(JdbcTemplate::class.java)
            val bob = jdbcTemplate.queryRequiredObject(SELECT_USER, User::class.java, 1)
            assertEquals("Bob Jones", bob.name)
            assertEquals(18, bob.age)
            assertThrows<DataAccessException> {
                // alice was deleted:
                jdbcTemplate.queryRequiredObject(SELECT_USER, User::class.java, 2)
            }
        }
    }
}