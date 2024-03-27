package com.example.autumn.jdbc.tx

import com.example.autumn.context.AnnotationConfigApplicationContext
import com.example.autumn.exception.TransactionException
import com.example.autumn.jdbc.JdbcTemplate
import com.example.autumn.jdbc.JdbcTestBase
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbcTxTest : JdbcTestBase() {
    @Test
    fun testJdbcWithTx() {
        AnnotationConfigApplicationContext(JdbcTxApplication::class.java, propertyResolver).use { ctx ->
            val jdbcTemplate = ctx.getBean(JdbcTemplate::class.java)
            jdbcTemplate.update(CREATE_USER)
            jdbcTemplate.update(CREATE_ADDRESS)

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
            assertThrows(TransactionException::class.java) {
                addressService.addAddress(addr1, addr2, addr3)
            }

            // ALL address should not be inserted:
            assertTrue(addressService.getAddresses(bob.id).isEmpty())

            // insert addr1, addr2 for Bob only:
            addressService.addAddress(addr1, addr2)
            assertEquals(2, addressService.getAddresses(bob.id).size)

            // now delete bob will cause rollback:
            assertThrows(TransactionException::class.java) {
                userService.deleteUser(bob)
            }

            // bob and his addresses still exist:
            assertEquals("Bob", userService.getUser(1).name)
            assertEquals(2, addressService.getAddresses(bob.id).size)
        }

        AnnotationConfigApplicationContext(JdbcTxApplication::class.java, propertyResolver).use { ctx ->
            val addressService: AddressService = ctx.getBean(AddressService::class.java)
            val addressesOfBob = addressService.getAddresses(1)
            assertEquals(2, addressesOfBob.size)
        }
    }
}