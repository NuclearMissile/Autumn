package com.example.autumn.orm

import com.example.autumn.context.AnnotationConfigApplicationContext
import com.example.autumn.jdbc.JdbcTemplate
import com.example.autumn.orm.entity.EventEntity
import com.example.autumn.orm.entity.PasswordAuthEntity
import com.example.autumn.orm.entity.UserEntity
import com.example.autumn.resolver.PropertyResolver
import org.junit.jupiter.api.BeforeEach
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

    @BeforeEach
    fun setUp() {
        Files.deleteIfExists(Path("test_orm.db"))
        AnnotationConfigApplicationContext(OrmTestApplication::class.java, propertyResolver).use { ctx ->
            val jdbcTemplate = ctx.getBean<JdbcTemplate>("jdbcTemplate")
            jdbcTemplate.update(CREATE_USERS)
            jdbcTemplate.update(CREATE_EVENTS)
            jdbcTemplate.update(CREATE_PASSWORD_AUTHS)
        }
    }

    @Test
    fun testExportDDL() {
        AnnotationConfigApplicationContext(OrmTestApplication::class.java, propertyResolver).use { ctx ->
            val dbTemplate = ctx.getBean<DbTemplate>("dbTemplate")
            val ddl = dbTemplate.exportDDL()
            println(ddl.slice(0 until 500))
            assertTrue(ddl.startsWith("CREATE TABLE api_key_auths ("))
        }
    }

    @Test
    fun testInsert() {
        AnnotationConfigApplicationContext(OrmTestApplication::class.java, propertyResolver).use { ctx ->
            val dbTemplate = ctx.getBean<DbTemplate>("dbTemplate")
            val timestamp = System.currentTimeMillis()
            val user = UserEntity(-1, 1, timestamp)
            dbTemplate.insert(UserEntity::class.java, user)
            assertNotEquals(-1, user.id)
            val selected = dbTemplate.selectById<UserEntity>(user.id)!!
            assertEquals(timestamp, selected.createdAt)
        }
    }

    @Test
    fun testBatchInsert() {
        AnnotationConfigApplicationContext(OrmTestApplication::class.java, propertyResolver).use { ctx ->
            val dbTemplate = ctx.getBean<DbTemplate>("dbTemplate")
            val users = (0 until 1000).map { UserEntity(-1, it, it.toLong()) }
            dbTemplate.batchInsert(UserEntity::class.java, users)
            val usersResult = dbTemplate.selectFrom<UserEntity>().orderBy("id").query()
            assertContentEquals(usersResult.map { it.id }, users.map { it.id })
            assertEquals(1000, usersResult.count())
            assertEquals(999, usersResult.last().id - usersResult.first().id)
            assertEquals(0, usersResult.first().type)

            val events = (0 until 1000L).map { EventEntity(it, it, "test", it) }
            dbTemplate.batchInsert(EventEntity::class.java, events)
            val eventsResult = dbTemplate.selectFrom<EventEntity>()
                .where("events.data = ?", "test")
                .query()
            assertEquals(1000, eventsResult.count())
        }
    }

    @Test
    fun testQueries() {
        AnnotationConfigApplicationContext(OrmTestApplication::class.java, propertyResolver).use { ctx ->
            val dbTemplate = ctx.getBean<DbTemplate>("dbTemplate")
            val users = (0 until 1000).map { UserEntity(-1, 1, it.toLong()) }
            val paes = (1000 until 2000).map {
                PasswordAuthEntity(
                    it.toLong(), if (it % 2 == 0) "1" else "2", it.toString()
                )
            }
            dbTemplate.batchInsert(UserEntity::class.java, users)
            dbTemplate.batchInsert(PasswordAuthEntity::class.java, paes)

            val result0 = dbTemplate.selectFrom<UserEntity>()
                .orderBy("id", true)
                .first()!!
            assertEquals(1000, result0.id)

            val result1 = dbTemplate.selectFrom<PasswordAuthEntity>()
                .where("random = ?", "1")
                .query()
            assertEquals(500, result1.count())

            val result2 = dbTemplate.selectFrom<UserEntity>()
                .limit(Long.MAX_VALUE, 950)
                .query()
            assertEquals(50, result2.count())

            val result3 = dbTemplate.selectFrom<PasswordAuthEntity>()
                .orderBy("random")
                .limit(501)
                .query()
            result3.slice(0 until 500).forEach { assertEquals("1", it.random) }
            assertEquals("2", result3.last().random)

            val result4 = dbTemplate.selectFrom<PasswordAuthEntity>()
                .join("users ON users.type = password_auths.random AND users.id % 2 = ?", 0)
                .where("users.type = password_auths.random")
                .query()
            result4.forEach { assertEquals("1", it.random) }

            val result5 = dbTemplate.selectFrom<PasswordAuthEntity>()
                .join("users ON users.type = password_auths.random AND users.id % 2 = ?", 0)
                .where("users.type = ?", "a")
                .query()
            assertEquals(0, result5.count())
        }
    }


    private val propertyResolver: PropertyResolver
        get() = PropertyResolver(
            mapOf(
                "autumn.datasource.url" to "jdbc:sqlite:test_orm.db",
                "autumn.datasource.username" to "sa",
                "autumn.datasource.password" to "",
                "autumn.datasource.driver-class-name" to "org.sqlite.JDBC",
                "autumn.db-template.entity-package-path" to "com.example.autumn.orm.entity",
            ).toProperties()
        )
}