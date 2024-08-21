package org.example.autumn.db.tx

import org.example.autumn.annotation.*
import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.db.*
import org.example.autumn.exception.DataAccessException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(DbConfiguration::class)
class JdbcTxTestConfiguration

@Component
@Transactional
class AddressService(
    @Autowired val jdbcTemplate: JdbcTemplate,
) {
    @Autowired
    lateinit var userService: UserService

    @Transactional
    fun addAddress(vararg addresses: Address) {
        for (address in addresses) {
            // check if userId exists:
            userService.getUser(address.userId)
            jdbcTemplate.update(JdbcTestBase.INSERT_ADDRESS, address.userId, address.address, address.zipcode)
        }
    }

    fun getAddresses(userId: Int): List<Address> {
        return jdbcTemplate.queryList(JdbcTestBase.SELECT_ADDRESS_BY_USERID, userId)
    }

    @Transactional
    fun deleteAddress(userId: Int) {
        jdbcTemplate.update(JdbcTestBase.DELETE_ADDRESS_BY_USERID, userId)
        if (userId == 1) {
            throw RuntimeException("Rollback delete for user id = 1")
        }
    }
}

@Component
@Transactional
class UserService(
    @Autowired val jdbcTemplate: JdbcTemplate,
) {
    @Autowired
    lateinit var addressService: AddressService

    @Transactional
    fun createUser(name: String?, age: Int): User {
        val id = jdbcTemplate.insert(JdbcTestBase.INSERT_USER, name, age)
        return User(id.toInt(), name, age)
    }

    fun getUser(userId: Int): User {
        return jdbcTemplate.queryRequired(JdbcTestBase.SELECT_USER, userId)
    }

    @Transactional
    fun updateUser(user: User) {
        jdbcTemplate.update(JdbcTestBase.UPDATE_USER, user.name, user.age, user.id)
    }

    @Transactional
    fun deleteUser(user: User) {
        jdbcTemplate.update(JdbcTestBase.DELETE_USER, user.id)
        addressService.deleteAddress(user.id)
    }
}


class JdbcTxTest : JdbcTestBase() {
    @Test
    fun testJdbcWithTx() {
        AnnotationConfigApplicationContext(JdbcTxTestConfiguration::class.java, config).use { ctx ->
            val jdbcTemplate = ctx.getBean(JdbcTemplate::class.java)
            jdbcTemplate.update(CREATE_USER_TABLE)
            jdbcTemplate.update(CREATE_ADDRESS_TABLE)

            val userService: UserService = ctx.getBean(UserService::class.java)
            val addressService: AddressService = ctx.getBean(AddressService::class.java)
            // proxied:
            assertNotSame(UserService::class.java, userService.javaClass)
            assertNotSame(AddressService::class.java, addressService.javaClass)
            // proxy object is not inject:
            val addressServiceField = UserService::class.java.getDeclaredField("addressService")
            addressServiceField.isAccessible = true
            assertNull(addressServiceField.get(userService))
            val userServiceField = AddressService::class.java.getDeclaredField("userService")
            userServiceField.isAccessible = true
            assertNull(userServiceField.get(addressService))

            // insert user:
            val bob = userService.createUser("Bob", 12)
            assertEquals(1, bob.id)

            // insert addresses:
            val addr1 = Address(0, bob.id, "Broadway, New York", 10012)
            val addr2 = Address(0, bob.id, "Fifth Avenue, New York", 10080)
            // user not exist for addr3:
            val addr3 = Address(0, bob.id + 1, "Ocean Drive, Miami, Florida", 33411)
            assertThrows<DataAccessException> {
                addressService.addAddress(addr1, addr2, addr3)
            }

            // ALL address should not be inserted:
            assertTrue(addressService.getAddresses(bob.id).isEmpty())

            // insert addr1, addr2 for Bob only:
            addressService.addAddress(addr1, addr2)
            assertEquals(2, addressService.getAddresses(bob.id).size)

            // now delete bob will cause rollback:
            assertThrows<RuntimeException> {
                userService.deleteUser(bob)
            }

            // bob and his addresses still exist:
            assertEquals("Bob", userService.getUser(1).name)
            assertEquals(2, addressService.getAddresses(bob.id).size)
        }

        AnnotationConfigApplicationContext(JdbcTxTestConfiguration::class.java, config).use { ctx ->
            val addressService: AddressService = ctx.getBean(AddressService::class.java)
            val addressesOfBob = addressService.getAddresses(1)
            assertEquals(2, addressesOfBob.size)
        }
    }
}