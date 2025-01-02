package io.nuclearmissile.autumn.exception

open class AutumnException(message: String?, cause: Throwable? = null) : Exception(message, cause)

open class BeanDefinitionException(message: String, cause: Throwable? = null) :
    AutumnException(message, cause)

open class BeanCreationException(message: String, cause: Throwable? = null) :
    AutumnException(message, cause)

open class ResponseErrorException(
    val statusCode: Int, message: String, cause: Throwable? = null,
) : AutumnException(message, cause)

class BeanTypeException(message: String) : BeanDefinitionException(message)
class NoSuchBeanException(message: String) : BeanDefinitionException(message)
class NoUniqueBeanException(message: String) : BeanDefinitionException(message)
class DependencyException(message: String) : BeanCreationException(message)

class AopConfigException(message: String, cause: Throwable? = null) : AutumnException(message, cause)

class DataAccessException(message: String, cause: Throwable? = null) : AutumnException(message, cause)

class NotFoundException(message: String, cause: Throwable? = null) : ResponseErrorException(404, message, cause)

class ServerErrorException(message: String, cause: Throwable? = null) : ResponseErrorException(500, message, cause)

class RequestErrorException(message: String, cause: Throwable? = null) : ResponseErrorException(400, message, cause)