package com.example.autumn.exception

open class AutumnException(message: String?, cause: Throwable?) : Throwable(message, cause)

open class BeanDefinitionException(override val message: String, override val cause: Throwable? = null) :
    AutumnException(message, cause)

open class BeanCreationException(override val message: String, override val cause: Throwable? = null) :
    AutumnException(message, cause)

open class ErrorResponseException(
    val statusCode: Int,
    override val message: String,
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

class ServerErrorException(override val message: String, override val cause: Throwable? = null) :
    ErrorResponseException(500, message, cause)

class RequestErrorException(override val message: String, override val cause: Throwable? = null) :
    ErrorResponseException(400, message, cause)