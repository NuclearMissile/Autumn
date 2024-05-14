package org.example.autumn.db.orm

import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.db.JdbcTemplate
import org.example.autumn.db.orm.entity.EventEntity
import org.example.autumn.db.orm.entity.PasswordAuthEntity
import org.example.autumn.db.orm.entity.UserEntity
import org.example.autumn.resolver.Config
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.*

class OrmTest {
    companion object {
        const val CREATE_USERS =
            "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, createdAt BIGINT NOT NULL, type INTEGER NOT NULL);"
        const val CREATE_EVENTS =
            "CREATE TABLE events (sequenceId INTEGER PRIMARY KEY AUTOINCREMENT, previousId BIGINT NOT NULL, data TEXT NOT NULL, createdAt BIGINT NOT NULL);"
        const val CREATE_PASSWORD_AUTHS =
            "CREATE TABLE password_auths (userId INTEGER PRIMARY KEY AUTOINCREMENT, random TEXT NOT NULL, passwd TEXT NOT NULL);"
    }

    private val config = Config(
        mapOf(
            "autumn.datasource.url" to "jdbc:sqlite:test_orm.db",
            "autumn.datasource.username" to "sa",
            "autumn.datasource.password" to "",
            "autumn.datasource.driver-class-name" to "org.sqlite.JDBC",
        ).toProperties()
    )

    @BeforeEach
    fun setUp() {
        Files.deleteIfExists(Path("test_orm.db"))
        AnnotationConfigApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val jdbcTemplate = ctx.getBean<JdbcTemplate>("jdbcTemplate")
            jdbcTemplate.update(CREATE_USERS)
            jdbcTemplate.update(CREATE_EVENTS)
            jdbcTemplate.update(CREATE_PASSWORD_AUTHS)
        }
    }

    @Test
    fun testExportDDL() {
        AnnotationConfigApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val ddl = naiveOrm.exportDDL()
            println(ddl.slice(0 until 500))
            assertTrue(ddl.startsWith("CREATE TABLE api_key_auths ("))
        }
    }

    @Test
    fun testInsert() {
        AnnotationConfigApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val timestamp = System.currentTimeMillis()
            val user = UserEntity(-1, 1, timestamp)
            naiveOrm.insert(user)
            assertNotEquals(-1, user.id)
            val selected = naiveOrm.selectById<UserEntity>(user.id)!!
            assertEquals(timestamp, selected.createdAt)
        }
    }

    @Test
    fun testBatchInsert() {
        AnnotationConfigApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val users = (0 until 1000).map { UserEntity(-1, it, it.toLong()) }
            naiveOrm.batchInsert(users)
            val usersResult = naiveOrm.selectFrom<UserEntity>().orderBy("id").query()
            assertContentEquals(usersResult.map { it.id }, users.map { it.id })
            assertEquals(1000, usersResult.count())
            assertEquals(999, usersResult.last().id - usersResult.first().id)
            assertEquals(0, usersResult.first().type)

            val events = (0 until 1000L).map { EventEntity(it, it, "test", it) }
            naiveOrm.batchInsert(events)
            val eventsResult = naiveOrm.selectFrom<EventEntity>()
                .where("events.data = ?", "test")
                .query()
            assertEquals(1000, eventsResult.count())
        }
    }

    @Test
    fun testQueries() {
        AnnotationConfigApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val users = (0 until 1000).map { UserEntity(-1, 1, it.toLong()) }
            val paes = (1000 until 2000).map {
                PasswordAuthEntity(
                    it.toLong(), if (it % 2 == 0) "1" else "2", it.toString()
                )
            }
            naiveOrm.batchInsert(users)
            naiveOrm.batchInsert(paes)

            val result0 = naiveOrm.selectFrom<UserEntity>()
                .orderBy("id", true)
                .first()!!
            assertEquals(1000, result0.id)

            val result1 = naiveOrm.selectFrom<PasswordAuthEntity>()
                .where("random = ?", "1")
                .query()
            assertEquals(500, result1.count())

            val result2 = naiveOrm.selectFrom<UserEntity>()
                .limit(Long.MAX_VALUE, 950)
                .query()
            assertEquals(50, result2.count())

            val result3 = naiveOrm.selectFrom<PasswordAuthEntity>()
                .orderBy("random")
                .limit(501)
                .query()
            result3.slice(0 until 500).forEach { assertEquals("1", it.random) }
            assertEquals("2", result3.last().random)

            val result4 = naiveOrm.selectFrom<PasswordAuthEntity>()
                .join("users ON users.type = password_auths.random AND users.id % 2 = ?", 0)
                .where("users.type = password_auths.random")
                .query()
            result4.forEach { assertEquals("1", it.random) }

            val result5 = naiveOrm.selectFrom<PasswordAuthEntity>()
                .join("users ON users.type = password_auths.random AND users.id % 2 = ?", 0)
                .where("users.type = ?", "a")
                .query()
            assertEquals(0, result5.count())
        }
    }

    @Test
    fun testUpdate() {
        AnnotationConfigApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val timestamp = System.currentTimeMillis()
            val user = UserEntity(-1, 1, timestamp)
            naiveOrm.insert(user)
            assertNotEquals(-1, user.id)
            val timestamp2 = System.currentTimeMillis()
            user.createdAt = timestamp2
            assertThrows<IllegalArgumentException> { naiveOrm.update(user) }

            val pae = PasswordAuthEntity(999, "-1", "foo")
            naiveOrm.insert(pae)
            pae.passwd = "bar"
            naiveOrm.update(pae)
            assertEquals("bar", naiveOrm.selectById<PasswordAuthEntity>(999)!!.passwd)
        }
    }

    @Test
    fun testDelete() {
        AnnotationConfigApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val timestamp = System.currentTimeMillis()
            val user = UserEntity(-1, 1, timestamp)
            naiveOrm.insert(user)
            assertNotEquals(-1, user.id)
            assertTrue(naiveOrm.selectById<UserEntity>(user.id) != null)
            naiveOrm.delete(user)
            assertNull(naiveOrm.selectById<UserEntity>(user.id))
        }
    }


    @Test
    fun testDeleteById() {
        AnnotationConfigApplicationContext(OrmTestConfiguration::class.java, config).use { ctx ->
            val naiveOrm = ctx.getBean<NaiveOrm>("naiveOrm")
            val timestamp = System.currentTimeMillis()
            val user = UserEntity(-1, 1, timestamp)
            naiveOrm.insert(user)
            assertNotEquals(-1, user.id)
            assertTrue(naiveOrm.selectById<UserEntity>(user.id) != null)
            naiveOrm.deleteById<UserEntity>(user.id)
            assertNull(naiveOrm.selectById<UserEntity>(user.id))
        }
    }
}