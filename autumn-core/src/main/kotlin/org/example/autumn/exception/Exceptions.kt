package org.example.autumn.exception

open class AutumnException(message: String?, cause: Throwable?) : Exception(message, cause)

open class BeanDefinitionException(message: String, cause: Throwable? = null) :
    AutumnException(message, cause)

open class BeanCreationException(message: String, cause: Throwable? = null) :
    AutumnException(message, cause)

open class ResponseErrorException(
    val statusCode: Int, message: String, val responseBody: String? = null, cause: Throwable? = null
) : AutumnException(message, cause)

class BeanTypeException(message: String) : BeanDefinitionException(message)
class NoSuchBeanException(message: String) : BeanDefinitionException(message)
class NoUniqueBeanException(message: String) : BeanDefinitionException(message)
class DependencyException(message: String) : BeanCreationException(message)

class AopConfigException(message: String, cause: Throwable? = null) :
    AutumnException(message, cause)

class DataAccessException(message: String, cause: Throwable? = null) :
    AutumnException(message, cause)

class TransactionException(message: String, cause: Throwable? = null) :
    AutumnException(message, cause)

class NotFoundException(message: String, responseBody: String? = null, cause: Throwable? = null) :
    ResponseErrorException(404, message, responseBody, cause)

class ServerErrorException(message: String, responseBody: String? = null, cause: Throwable? = null) :
    ResponseErrorException(500, message, responseBody, cause)

class RequestErrorException(message: String, responseBody: String? = null, cause: Throwable? = null) :
    ResponseErrorException(400, message, responseBody, cause)