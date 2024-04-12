package org.example.autumn.exception

open class AutumnException(message: String?, cause: Throwable?) : Throwable(message, cause)

open class BeanDefinitionException(override val message: String, override val cause: Throwable? = null) :
    AutumnException(message, cause)

open class BeanCreationException(override val message: String, override val cause: Throwable? = null) :
    AutumnException(message, cause)

open class AbnormalResponseException(
    val statusCode: Int,
    override val message: String,
    val responseBody: String? = null,
    override val cause: Throwable? = null
) : AutumnException(message, cause)

class BeanTypeException(override val message: String) : BeanDefinitionException(message)
class NoSuchBeanException(override val message: String) : BeanDefinitionException(message)
class NoUniqueBeanException(override val message: String) : BeanDefinitionException(message)
class DependencyException(override val message: String) : BeanCreationException(message)

class AopConfigException(override val message: String, override val cause: Throwable? = null) :
    AutumnException(message, cause)

class DataAccessException(override val message: String, override val cause: Throwable? = null) :
    AutumnException(message, cause)

class TransactionException(override val message: String, override val cause: Throwable? = null) :
    AutumnException(message, cause)

class NotFoundException(override val message: String) : AbnormalResponseException(404, message, null, null)

class ServerErrorException(
    override val message: String,
    responseBody: String? = null,
    override val cause: Throwable? = null
) : AbnormalResponseException(500, message, responseBody, cause)

class RequestErrorException(
    override val message: String,
    responseBody: String? = null,
    override val cause: Throwable? = null
) : AbnormalResponseException(400, message, responseBody, cause)