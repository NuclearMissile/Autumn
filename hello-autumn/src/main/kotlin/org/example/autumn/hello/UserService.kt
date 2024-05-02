package org.example.autumn.hello

import org.example.autumn.annotation.Component

data class User(val email: String, val name: String, val password: String)

@Component
class UserService {
    companion object {
        private val db = mutableMapOf<String, User>(
            "test@test.com" to User("test@test.com", "test", "test"),
        )
    }

    fun getUser(email: String): User? {
        return db[email]
    }

    fun createUser(email: String, name: String, password: String): User {
        val user = User(email, name, password)
        require(!db.containsKey(email)) { "$user is already registered" }
        db[email] = user
        return user
    }
}