package com.example.autumn.orm

import com.example.autumn.context.AnnotationConfigApplicationContext
import com.example.autumn.resolver.PropertyResolver
import kotlin.test.Test
import kotlin.test.assertTrue

class OrmTest {
    @Test
    fun testExportDDL() {
        AnnotationConfigApplicationContext(OrmTestApplication::class.java, propertyResolver).use { ctx ->
            val testDbTemplate = ctx.getBean<DbTemplate>("testDbTemplate")
            val ddl = testDbTemplate.exportDDL()
            println(ddl.slice(0 until 500))
            assertTrue(ddl.startsWith("CREATE TABLE api_key_auths ("))
        }
    }

    private val propertyResolver: PropertyResolver
        get() = PropertyResolver(
            mapOf(
                "autumn.datasource.url" to "jdbc:sqlite:test.db",
                "autumn.datasource.username" to "sa",
                "autumn.datasource.password" to "",
                "autumn.datasource.driver-class-name" to "org.sqlite.JDBC",
                "autumn.db-template.entity-package-path" to "com.example.autumn.orm.entity",
            ).toProperties()
        )

}