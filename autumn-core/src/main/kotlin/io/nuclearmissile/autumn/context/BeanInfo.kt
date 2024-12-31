package io.nuclearmissile.autumn.context

import io.nuclearmissile.autumn.annotation.Configuration
import io.nuclearmissile.autumn.exception.BeanCreationException
import java.lang.reflect.Constructor
import java.lang.reflect.Method

interface IBeanInfo : Comparable<IBeanInfo> {
    val beanName: String
    val beanClass: Class<*>
    val order: Int
    val isPrimary: Boolean
    var beanCtor: Constructor<*>?
    var factoryName: String?
    var factoryMethod: Method?
    var initMethodName: String?
    var initMethod: Method?
    var destroyMethodName: String?
    var destroyMethod: Method?
    var instance: Any?
    val requiredInstance: Any
    val aopBeanInfos: MutableList<IBeanInfo>
    val isConfiguration: Boolean
    val isBeanPostProcessor: Boolean
}

class BeanInfo private constructor(
    override val beanName: String,
    override val beanClass: Class<*>,
    override val order: Int,
    override val isPrimary: Boolean,
) : IBeanInfo {
    override var beanCtor: Constructor<*>? = null
    override var factoryName: String? = null
    override var factoryMethod: Method? = null
    override var initMethodName: String? = null
    override var initMethod: Method? = null
    override var destroyMethodName: String? = null
    override var destroyMethod: Method? = null

    override var instance: Any? = null
        set(value) {
            requireNotNull(value) { "Bean instance is null." }
            require(beanClass.isAssignableFrom(value.javaClass)) {
                throw BeanCreationException("Instance $value of Bean ${value.javaClass.name} is not the expected type: ${beanClass.name}")
            }
            field = value
        }
    override val requiredInstance: Any
        get() = instance ?: throw BeanCreationException(
            "Instance of bean with name $beanName and type ${beanClass.name} is not instantiated during current stage.",
        )

    override val aopBeanInfos = mutableListOf<IBeanInfo>()
    override val isConfiguration: Boolean = beanClass.isAnnotationPresent(Configuration::class.java)
    override val isBeanPostProcessor: Boolean = BeanPostProcessor::class.java.isAssignableFrom(beanClass)

    constructor(
        beanName: String, beanClass: Class<*>, order: Int, isPrimary: Boolean, beanCtor: Constructor<*>,
        initMethod: Method?, destroyMethod: Method?,
    ) : this(beanName, beanClass, order, isPrimary) {
        beanCtor.isAccessible = true
        this.beanCtor = beanCtor
        this.initMethod = initMethod
        this.destroyMethod = destroyMethod
        this.initMethod?.isAccessible = true
        this.destroyMethod?.isAccessible = true
    }

    constructor(
        beanName: String, beanClass: Class<*>, order: Int, isPrimary: Boolean, factoryName: String,
        factoryMethod: Method, initMethodName: String?, destroyMethodName: String?,
    ) : this(beanName, beanClass, order, isPrimary) {
        this.factoryName = factoryName
        this.factoryMethod = factoryMethod
        this.factoryMethod?.isAccessible = true
        this.initMethodName = initMethodName
        this.destroyMethodName = destroyMethodName
    }

    override fun compareTo(other: IBeanInfo): Int {
        val orderCmp = order.compareTo(other.order)
        return if (orderCmp == 0) beanName.compareTo(other.beanName) else orderCmp
    }

    override fun toString(): String {
        return "BeanInfo(beanName='$beanName', beanClass=$beanClass, order=$order, isPrimary=$isPrimary, " +
                "instance=$instance, beanCtor=$beanCtor, factoryName=$factoryName, " +
                "initMethodName=$initMethodName, destroyMethodName=$destroyMethodName)"
    }
}