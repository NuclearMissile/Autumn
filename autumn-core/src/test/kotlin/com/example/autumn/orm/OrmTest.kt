package com.example.autumn.orm

import com.example.autumn.context.AnnotationConfigApplicationContext
import com.example.autumn.jdbc.JdbcTemplate
import com.example.autumn.orm.entity.UserEntity
import com.example.autumn.resolver.PropertyResolver
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OrmTest {
    @BeforeEach
    fun setUp() {
        Files.deleteIfExists(Path("test_orm.db"))
        AnnotationConfigApplicationContext(OrmTestApplication::class.java, propertyResolver).use { ctx ->
            val jdbcTemplate = ctx.getBean<JdbcTemplate>("jdbcTemplate")
            val userDDL = "CREATE TABLE users (\n" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "  createdAt BIGINT NOT NULL,\n" +
                    "  type INTEGER NOT NULL);\n"
            jdbcTemplate.update(userDDL)
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
            val dbTemplate = ctx.getBean<DbTemplate>("testDbTemplate")
            val timestamp = System.currentTimeMillis()
            val user = UserEntity(-1, 1, timestamp)
            dbTemplate.insert(UserEntity::class.java, user)
            assertNotEquals(-1, user.id)
            val selected = dbTemplate.selectById<UserEntity>(user.id)!!
            assertEquals(timestamp, selected.createdAt)
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