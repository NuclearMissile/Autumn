package org.example.autumn.db.no_tx

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.example.autumn.annotation.*
import org.example.autumn.context.AnnotationApplicationContext
import org.example.autumn.db.JdbcTemplate
import org.example.autumn.db.JdbcTestBase
import org.example.autumn.db.User
import org.example.autumn.exception.DataAccessException
import org.junit.jupiter.api.assertThrows
import javax.sql.DataSource
import kotlin.test.*

@Configuration
class JdbcNoTxConfiguration {
    @Bean(destroyMethod = "close")
    fun dataSource(
        // properties:
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
}

class JdbcNoTxTest : JdbcTestBase() {
    @Test
    fun testJdbcNoTx() {
        AnnotationApplicationContext(JdbcNoTxConfiguration::class.java, config).use { ctx ->
            val jdbcTemplate = ctx.getUniqueBean(JdbcTemplate::class.java)
            jdbcTemplate.update(CREATE_USER_TABLE)
            jdbcTemplate.update(CREATE_ADDRESS_TABLE)
            // insert user:
            val userId1 = jdbcTemplate.insert(INSERT_USER, "Bob", 12).toInt()
            val userId2 = jdbcTemplate.insert(INSERT_USER, "Alice", null).toInt()
            assertEquals(1, userId1)
            assertEquals(2, userId2)
            // query user:
            val bob = jdbcTemplate.querySingle<User>(SELECT_USER, userId1)
            val alice = jdbcTemplate.querySingle<User>(SELECT_USER, userId2)
            assertEquals(1, bob.id)
            assertEquals("Bob", bob.name)
            assertEquals(12, bob.age)
            assertEquals(2, alice.id)
            assertEquals("Alice", alice.name)
            assertNull(alice.age)

            val list1 = jdbcTemplate.queryList<User>(SELECT_ALL_USER)
            assertEquals(bob, list1[0])
            assertEquals(alice, list1[1])

            // query name:
            assertEquals("Bob", jdbcTemplate.querySingle(SELECT_USER_NAME, userId1))
            assertEquals(12, jdbcTemplate.querySingle(SELECT_USER_AGE, userId1))
            // update user:
            val n1 = jdbcTemplate.update(UPDATE_USER, "Bob Jones", 18, bob.id)
            assertEquals(1, n1)
            // delete user:
            val n2 = jdbcTemplate.update(DELETE_USER, alice.id)
            assertEquals(1, n2)

            val list2 = jdbcTemplate.queryList<User>(SELECT_ALL_USER)
            assertEquals(1, list2.size)
        }

        AnnotationApplicationContext(JdbcNoTxConfiguration::class.java, config).use { ctx ->
            val jdbcTemplate = ctx.getUniqueBean(JdbcTemplate::class.java)
            val bob = jdbcTemplate.querySingle<User>(SELECT_USER, 1)
            assertEquals("Bob Jones", bob.name)
            assertEquals(18, bob.age)
            assertThrows<DataAccessException> {
                // alice was deleted:
                jdbcTemplate.querySingle(SELECT_USER, 2)
            }
        }
    }

    @Ignore("batchInsert return generated keys not supported by sqlite")
    @Test
    fun testBatchInsert() {
        AnnotationApplicationContext(JdbcNoTxConfiguration::class.java, config).use { ctx ->
            val jdbcTemplate = ctx.getUniqueBean(JdbcTemplate::class.java)
            jdbcTemplate.update(CREATE_USER_TABLE)

            val ids = jdbcTemplate.batchInsert(INSERT_USER, 2, "Alice", 12, "Bob", null)
            assertEquals(1, ids[1] - ids[0])
        }
    }

    @Test
    fun testQueryList() {
        AnnotationApplicationContext(JdbcNoTxConfiguration::class.java, config).use { ctx ->
            val jdbcTemplate = ctx.getUniqueBean(JdbcTemplate::class.java)
            jdbcTemplate.update(CREATE_USER_TABLE)

            val userId1 = jdbcTemplate.insert(INSERT_USER, "Alice", 12).toInt()
            val list1 = jdbcTemplate.query<List<*>>(SELECT_USER, userId1)
            assertEquals("[$userId1, Alice, 12, 0, 0, 1]", list1.toString())

            val userId2 = jdbcTemplate.insert(INSERT_USER, "Bob", null).toInt()
            val list2 = jdbcTemplate.query<MutableList<*>>(SELECT_USER, userId2)
            assertEquals("[$userId2, Bob, null, 0, 0, 1]", list2.toString())

            val list3 = jdbcTemplate.queryList<List<*>>(SELECT_ALL_USER)
            assertContentEquals(list1, list3[0])
            assertContentEquals(list2, list3[1])

            val userCount = jdbcTemplate.query<Int>(SELECT_USER_COUNT)
            assertEquals(2, userCount)

            jdbcTemplate.query<Byte>(SELECT_USER_COUNT)
            jdbcTemplate.query<Short>(SELECT_USER_COUNT)
            jdbcTemplate.query<Int>(SELECT_USER_COUNT)
            jdbcTemplate.query<Long>(SELECT_USER_COUNT)
            jdbcTemplate.query<Number>(SELECT_USER_COUNT)

            assertThrows<IllegalArgumentException> { jdbcTemplate.query<ArrayList<*>>(SELECT_USER, userId1) }
        }
    }

    @Test
    fun testQueryMap() {
        AnnotationApplicationContext(JdbcNoTxConfiguration::class.java, config).use { ctx ->
            val jdbcTemplate = ctx.getUniqueBean(JdbcTemplate::class.java)
            jdbcTemplate.update(CREATE_USER_TABLE)

            val userId1 = jdbcTemplate.insert(INSERT_USER, "Alice", 12).toInt()
            val map1 = jdbcTemplate.query<Map<String, *>>(SELECT_USER, userId1)
            assertEquals(
                "{id=$userId1, name=Alice, age=12, booleanTest=0, shortTest=0, isTest=1}", map1.toString()
            )

            val userId2 = jdbcTemplate.insert(INSERT_USER, "Bob", null).toInt()
            val map2 = jdbcTemplate.query<MutableMap<String, *>>(SELECT_USER, userId2)
            assertEquals(
                "{id=$userId2, name=Bob, age=null, booleanTest=0, shortTest=0, isTest=1}", map2.toString()
            )

            val list3 = jdbcTemplate.queryList<Map<String, *>>(SELECT_ALL_USER)
            assertEquals(map1.toString(), list3[0].toString())
            assertEquals(map2.toString(), list3[1].toString())

            assertThrows<IllegalArgumentException> { jdbcTemplate.query<HashMap<String, *>>(SELECT_USER, userId1) }
        }
    }
}