package org.example.autumn.jdbc.tx

import org.example.autumn.annotation.*
import org.example.autumn.aop.AroundProxyBeanPostProcessor
import org.example.autumn.aop.BeforeInvocationHandlerAdapter
import org.example.autumn.jdbc.JdbcConfiguration
import org.example.autumn.jdbc.JdbcTemplate
import org.example.autumn.jdbc.JdbcTestBase
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

@ComponentScan
@Configuration
@Import(JdbcConfiguration::class)
class JdbcTxApplication

@Configuration
class BeforeApplication {
    @Bean
    fun createAroundProxyBeanPostProcessor(): AroundProxyBeanPostProcessor {
        return AroundProxyBeanPostProcessor()
    }
}

@Component
class LogInvocationHandler : BeforeInvocationHandlerAdapter() {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun before(proxy: Any, method: Method, args: Array<Any>?) {
        if (method.isAnnotationPresent(WithTransaction::class.java))
            logger.info("[Before] {}()", method.name)
    }
}

class Address(
    var id: Int = 0,
    var userId: Int = 0,
    var address: String? = null,
    var zipcode: Int = 0
)

class User(var id: Int = 0, var name: String? = null, var age: Int? = null)

@Component
@Transactional
// @Around("logInvocationHandler")
class AddressService {
    @Autowired
    var userService: UserService? = null

    @Autowired
    var jdbcTemplate: JdbcTemplate? = null

    @WithTransaction
    fun addAddress(vararg addresses: Address) {
        for (address in addresses) {
            // check if userId exists:
            userService!!.getUser(address.userId)
            jdbcTemplate!!.update(JdbcTestBase.INSERT_ADDRESS, address.userId, address.address, address.zipcode)
        }
    }

    @WithTransaction
    fun getAddresses(userId: Int): List<Address> {
        return jdbcTemplate!!.queryList(JdbcTestBase.SELECT_ADDRESS_BY_USERID, Address::class.java, userId)
    }

    @WithTransaction
    fun deleteAddress(userId: Int) {
        jdbcTemplate!!.update(JdbcTestBase.DELETE_ADDRESS_BY_USERID, userId)
        if (userId == 1) {
            throw RuntimeException("Rollback delete for user id = 1")
        }
    }
}

@Component
@Transactional
class UserService {
    @Autowired
    var addressService: AddressService? = null

    @Autowired
    var jdbcTemplate: JdbcTemplate? = null

    @WithTransaction
    fun createUser(name: String?, age: Int): User {
        val id: Number = jdbcTemplate!!.updateWithGeneratedKey(JdbcTestBase.INSERT_USER, name, age)
        val user = User()
        user.id = id.toInt()
        user.name = name
        user.age = age
        return user
    }

    @WithTransaction
    fun getUser(userId: Int): User {
        return jdbcTemplate!!.queryRequiredObject(JdbcTestBase.SELECT_USER, User::class.java, userId)
    }

    @WithTransaction
    fun updateUser(user: User) {
        jdbcTemplate!!.update(JdbcTestBase.UPDATE_USER, user.name, user.age, user.id)
    }

    @WithTransaction
    fun deleteUser(user: User) {
        jdbcTemplate!!.update(JdbcTestBase.DELETE_USER, user.id)
        addressService!!.deleteAddress(user.id)
    }
}
