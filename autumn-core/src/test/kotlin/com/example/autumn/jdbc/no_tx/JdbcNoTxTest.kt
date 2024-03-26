package com.example.autumn.jdbc.no_tx

import com.example.autumn.context.AnnotationConfigApplicationContext
import com.example.autumn.exception.DataAccessException
import com.example.autumn.jdbc.JdbcTemplate
import com.example.autumn.jdbc.JdbcTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import kotlin.test.assertEquals

class JdbcWithoutTxTest : JdbcTestBase() {
    @Test
    fun testJdbcWithoutTx() {
        AnnotationConfigApplicationContext(JdbcWithoutTxApplication::class.java, propertyResolver).use { ctx ->
            val jdbcTemplate = ctx.getBean(JdbcTemplate::class.java)
            jdbcTemplate.update(CREATE_USER)
            jdbcTemplate.update(CREATE_ADDRESS)
            // insert user:
            val userId1 = jdbcTemplate.updateWithGeneratedKey(INSERT_USER, "Bob", 12).toInt()
            val userId2 = jdbcTemplate.updateWithGeneratedKey(INSERT_USER, "Alice", null).toInt()
            Assertions.assertEquals(1, userId1)
            Assertions.assertEquals(2, userId2)
            // query user:
            val bob = jdbcTemplate.queryRequiredObject(SELECT_USER, User::class.java, userId1)
            val alice = jdbcTemplate.queryRequiredObject(SELECT_USER, User::class.java, userId2)
            assertEquals(1, bob.id)
            assertEquals("Bob", bob.name)
            assertEquals(12, bob.age)
            assertEquals(2, alice.id)
            assertEquals("Alice", alice.name)
            Assertions.assertNull(alice.age)
            // query name:
            assertEquals("Bob", jdbcTemplate.queryRequiredObject(SELECT_USER_NAME, String::class.java, userId1))
            assertEquals(12, jdbcTemplate.queryRequiredObject(SELECT_USER_AGE, Int::class.java, userId1))
            // update user:
            val n1 = jdbcTemplate.update(UPDATE_USER, "Bob Jones", 18, bob.id)
            Assertions.assertEquals(1, n1)
            // delete user:
            val n2 = jdbcTemplate.update(DELETE_USER, alice.id)
            Assertions.assertEquals(1, n2)
        }

        AnnotationConfigApplicationContext(JdbcWithoutTxApplication::class.java, propertyResolver).use { ctx ->
            val jdbcTemplate = ctx.getBean(JdbcTemplate::class.java)
            val bob = jdbcTemplate.queryRequiredObject(SELECT_USER, User::class.java, 1)
            assertEquals("Bob Jones", bob.name)
            assertEquals(18, bob.age)
            Assertions.assertThrows(
                DataAccessException::class.java,
                Executable {
                    // alice was deleted:
                    jdbcTemplate.queryRequiredObject(SELECT_USER, User::class.java, 2)
                })
        }
    }
}