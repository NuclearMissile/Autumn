package com.example.autumn.jdbc

import com.example.autumn.resolver.PropertyResolver
import org.junit.jupiter.api.BeforeEach
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path

open class JdbcTestBase {
    val CREATE_USER: String =
        "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(255) NOT NULL, age INTEGER)"
    val CREATE_ADDRESS: String =
        "CREATE TABLE addresses (id INTEGER PRIMARY KEY AUTOINCREMENT, userId INTEGER NOT NULL, address VARCHAR(255) NOT NULL, zip INTEGER)"

    val INSERT_USER: String = "INSERT INTO users (name, age) VALUES (?, ?)"
    val INSERT_ADDRESS: String = "INSERT INTO addresses (userId, address, zip) VALUES (?, ?, ?)"

    val UPDATE_USER: String = "UPDATE users SET name = ?, age = ? WHERE id = ?"
    val UPDATE_ADDRESS: String = "UPDATE addresses SET address = ?, zip = ? WHERE id = ?"

    val DELETE_USER: String = "DELETE FROM users WHERE id = ?"
    val DELETE_ADDRESS_BY_USERID: String = "DELETE FROM addresses WHERE userId = ?"

    val SELECT_USER: String = "SELECT * FROM users WHERE id = ?"
    val SELECT_USER_NAME: String = "SELECT name FROM users WHERE id = ?"
    val SELECT_USER_AGE: String = "SELECT age FROM users WHERE id = ?"
    val SELECT_ADDRESS_BY_USERID: String = "SELECT * FROM addresses WHERE userId = ?"

    @BeforeEach
    fun beforeEach() {
        val db = Path.of("test.db").normalize().toAbsolutePath()
        try {
            Files.deleteIfExists(db)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    val propertyResolver: PropertyResolver
        get() = PropertyResolver(
            mapOf(
                "autumn.datasource.url" to "jdbc:sqlite:test.db",
                "autumn.datasource.username" to "sa",
                "autumn.datasource.password" to "",
                "autumn.datasource.driver-class-name" to "org.sqlite.JDBC"
            ).toProperties()
        )
}