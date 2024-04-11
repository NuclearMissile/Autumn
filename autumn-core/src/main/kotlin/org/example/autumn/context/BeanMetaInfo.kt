package org.example.autumn.context

import org.example.autumn.annotation.Configuration
import org.example.autumn.exception.BeanCreationException
import org.example.autumn.utils.ClassUtils.findAnnotation
import java.lang.reflect.Constructor
import java.lang.reflect.Method

class BeanMetaInfo private constructor(
    val beanName: String, val beanClass: Class<*>, val order: Int, val isPrimary: Boolean
) : Comparable<BeanMetaInfo> {
    var instance: Any? = null
        set(value) {
            requireNotNull(value) { "Bean instance is null." }
            require(beanClass.isAssignableFrom(value.javaClass)) {
                throw BeanCreationException("Instance $value of Bean ${value.javaClass.name} is not the expected type: ${beanClass.name}")
            }
            field = value
        }
    var beanCtor: Constructor<*>? = null
        private set
    var factoryName: String? = null
        private set
    var factoryMethod: Method? = null
        private set
    var initMethodName: String? = null
        private set
    var initMethod: Method? = null
        private set
    var destroyMethodName: String? = null
        private set
    var destroyMethod: Method? = null
        private set
    val isConfiguration: Boolean
        get() = findAnnotation(beanClass, Configuration::class.java) != null
    val isBeanPostProcessor: Boolean
        get() = BeanPostProcessor::class.java.isAssignableFrom(beanClass)

    constructor(
        beanName: String, beanClass: Class<*>, order: Int, isPrimary: Boolean, beanCtor: Constructor<*>,
        initMethodName: String?, destroyMethodName: String?, initMethod: Method?, destroyMethod: Method?
    ) : this(beanName, beanClass, order, isPrimary) {
        beanCtor.isAccessible = true
        this.beanCtor = beanCtor
        setInitAndDestroyMethods(initMethodName, destroyMethodName, initMethod, destroyMethod)
    }

    constructor(
        beanName: String, beanClass: Class<*>, order: Int, isPrimary: Boolean, factoryName: String,
        factoryMethod: Method, initMethodName: String?, destroyMethodName: String?,
        initMethod: Method?, destroyMethod: Method?
    ) : this(beanName, beanClass, order, isPrimary) {
        this.factoryName = factoryName
        factoryMethod.isAccessible = true
        this.factoryMethod = factoryMethod
        setInitAndDestroyMethods(initMethodName, destroyMethodName, initMethod, destroyMethod)
    }

    fun getRequiredInstance(): Any {
        return instance ?: throw BeanCreationException(
            "Instance of bean with name $beanName and type ${beanClass.name} is not instantiated during current stage.",
        )
    }

    private fun setInitAndDestroyMethods(
        initMethodName: String?, destroyMethodName: String?, initMethod: Method?, destroyMethod: Method?
    ) {
        this.initMethodName = initMethodName
        this.destroyMethodName = destroyMethodName
        if (initMethod != null) {
            initMethod.isAccessible = true
            this.initMethod = initMethod
        }
        if (destroyMethod != null) {
            destroyMethod.isAccessible = true
            this.destroyMethod = destroyMethod
        }
    }


    override fun compareTo(other: BeanMetaInfo): Int {
        val orderCmp = order.compareTo(other.order)
        return if (orderCmp == 0) beanName.compareTo(other.beanName) else orderCmp
    }

    override fun toString(): String {
        return "BeanMetaInfo(beanName='$beanName', beanClass=$beanClass, order=$order, isPrimary=$isPrimary, " +
            "instance=$instance, beanCtor=$beanCtor, factoryName=$factoryName, " +
            "initMethodName=$initMethodName, destroyMethodName=$destroyMethodName)"
    }
}