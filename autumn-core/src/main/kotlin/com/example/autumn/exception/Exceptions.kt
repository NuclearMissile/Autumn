package com.example.autumn.exception

open class BeanDefinitionException(override val message: String?, override val cause: Throwable? = null) : Throwable()
open class BeanCreationException(override val message: String?, override val cause: Throwable? = null) : Throwable()

class BeanTypeException(override val message: String) : BeanDefinitionException(message)
class NoSuchBeanException(override val message: String) : BeanDefinitionException(message)
class NoUniqueBeanException(override val message: String) : BeanDefinitionException(message)
class DependencyException(override val message: String) : BeanCreationException(message)

class AopConfigException(override val message: String?, override val cause: Throwable? = null) : Throwable()