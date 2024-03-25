package com.example.autumn.context

import com.example.autumn.annotation.*
import com.example.autumn.exception.*
import com.example.autumn.io.PropertyResolver
import com.example.autumn.io.ResourceResolver
import com.example.autumn.utils.ClassUtils.findAnnotation
import com.example.autumn.utils.ClassUtils.findAnnotationMethod
import com.example.autumn.utils.ClassUtils.getAnnotation
import com.example.autumn.utils.ClassUtils.getBeanName
import com.example.autumn.utils.ClassUtils.getNamedMethod
import org.slf4j.LoggerFactory
import java.lang.reflect.*
import java.util.*

class AnnotationConfigApplicationContext private constructor(
    private val propertyResolver: PropertyResolver
) : ConfigurableApplicationContext {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val infos = mutableMapOf<String, BeanMetaInfo>()
    private val postProcessors = mutableListOf<BeanPostProcessor>()
    private val creatingBeanNames = mutableSetOf<String>()

    constructor(configClass: Class<*>, propertyResolver: PropertyResolver) : this(propertyResolver) {
        ApplicationContextHolder.applicationContext = this
        infos += createBeanMetaInfos(scanForClassNames(configClass))
        infos.values.filter { it.isConfiguration }.sorted().forEach(::createBean)
        postProcessors += infos.values.filter { it.isBeanPostProcessor }.sorted().map {
            createBean(it) as BeanPostProcessor
        }
        // 创建其他普通Bean:
        infos.values.sorted().forEach { if (it.instance == null) createBean(it) }
        // 通过字段和set方法注入依赖:
        infos.values.forEach {
            try {
                injectProperties(it, it.beanClass, getProxiedInstance(it))
            } catch (e: ReflectiveOperationException) {
                throw BeanCreationException("Error while injectBean for $it", e)
            }
        }
        // call init method
        infos.values.forEach {
            val proxied = getProxiedInstance(it)
            invokeMethod(proxied, it.initMethod, it.initMethodName)
            postProcessors.forEach { postProcessor ->
                val processed = postProcessor.afterInitialization(it.instance!!, it.beanName)
                if (processed !== it.instance!!) {
                    logger.atDebug().log(
                        "BeanPostProcessor {} return different bean from {} to {}.",
                        postProcessor.javaClass.name, it.instance!!.javaClass.name, processed.javaClass.name
                    )
                    it.instance = processed
                }
            }
        }
        if (logger.isDebugEnabled) {
            infos.values.sorted().forEach { logger.debug("bean initialized: $it") }
        }
    }

    private fun scanForClassNames(configClass: Class<*>): Set<String> {
        val scanAnno = findAnnotation(configClass, ComponentScan::class.java)
        val scanPackages = if (scanAnno == null || scanAnno.value.isEmpty())
            arrayOf(configClass.packageName) else scanAnno.value
        logger.atInfo().log("component scan in packages: {}", scanPackages.contentToString())

        val classNameSet = scanPackages.flatMap {
            logger.atDebug().log("scan package: {}", it)
            ResourceResolver(it).scanResources { res ->
                if (res.name.endsWith(".class"))
                    res.name.removeSuffix(".class").replace("/", ".").replace("\\", ".")
                else null
            }
        }.also { logger.atDebug().log("class found by component scan: {}", it) }.toMutableSet()

        configClass.getAnnotation(Import::class.java)?.value?.forEach {
            val importClassName = it.java.name
            if (classNameSet.contains(importClassName)) {
                logger.warn("ignore import: $importClassName for it is already been scanned.")
            } else {
                logger.debug("class found by import: {}", importClassName)
                classNameSet.add(importClassName)
            }
        }
        return classNameSet
    }

    private fun createBeanMetaInfos(classNameSet: Set<String>): Map<String, BeanMetaInfo> {
        val infos = mutableMapOf<String, BeanMetaInfo>()
        for (className in classNameSet) {
            // 获取Class:
            val clazz = try {
                Class.forName(className)
            } catch (e: ClassNotFoundException) {
                throw BeanCreationException("Class not found for name: $className", e)
            }
            if (clazz.isAnnotation || clazz.isEnum || clazz.isInterface || clazz.isRecord) {
                continue
            }

            // 是否标注@Component?
            findAnnotation(clazz, Component::class.java) ?: continue
            logger.atDebug().log("found component: {}", clazz.name)
            val mod = clazz.modifiers
            if (Modifier.isAbstract(mod)) {
                throw BeanDefinitionException("@Component class ${clazz.name} must not be abstract.")
            }
            if (Modifier.isPrivate(mod)) {
                throw BeanDefinitionException("@Component class ${clazz.name} must not be private.")
            }

            val beanName = getBeanName(clazz)
            val info = BeanMetaInfo(
                beanName, clazz, clazz.getOrder(), clazz.isPrimary(), selectConstructor(clazz),
                null, null, findAnnotationMethod(clazz, PostConstruct::class.java),
                findAnnotationMethod(clazz, PreDestroy::class.java)
            )
            if (infos.put(info.beanName, info) != null) {
                throw BeanDefinitionException("Duplicate bean name: ${info.beanName}")
            }
            logger.atDebug().log("define bean: {}", info)

            // handle factory method
            findAnnotation(clazz, Configuration::class.java) ?: continue
            if (BeanPostProcessor::class.java.isAssignableFrom(clazz)) {
                throw BeanDefinitionException("@Configuration class '${clazz.name}' cannot be BeanPostProcessor.")
            }
            scanFactoryMethods(beanName, clazz, infos)
        }
        return infos
    }

    private fun Class<*>.getOrder() = this.getAnnotation(Order::class.java)?.value ?: Int.MAX_VALUE
    private fun Method.getOrder() = this.getAnnotation(Order::class.java)?.value ?: Int.MAX_VALUE
    private fun Class<*>.isPrimary() = this.isAnnotationPresent(Primary::class.java)
    private fun Method.isPrimary() = this.isAnnotationPresent(Primary::class.java)

    private fun selectConstructor(clazz: Class<*>): Constructor<*> {
        var ctors = clazz.constructors
        if (ctors.isEmpty()) {
            ctors = clazz.declaredConstructors
            if (ctors.size != 1) {
                throw BeanDefinitionException("More than 1 constructor found in class: ${clazz.name}.")
            }
        }
        if (ctors.size != 1) {
            throw BeanDefinitionException("More than 1 public constructor found in class: ${clazz.name}.")
        }
        return ctors.single()
    }

    /**
     * Scan factory method that annotated with @Bean:
     *
     * <code>
     * &#64;Configuration
     * public class Hello {
     *     @Bean
     *     ZoneId createZone() {
     *         return ZoneId.of("Z");
     *     }
     * }
     * </code>
     */
    private fun scanFactoryMethods(
        factoryBeanName: String, factoryClass: Class<*>, infos: MutableMap<String, BeanMetaInfo>
    ) {
        for (method in factoryClass.declaredMethods) {
            val bean = method.getAnnotation(Bean::class.java) ?: continue
            val mod = method.modifiers
            if (Modifier.isAbstract(mod))
                throw BeanDefinitionException("@Bean method ${factoryClass.name}.${method.name} cannot be abstract")
            if (Modifier.isFinal(mod))
                throw BeanDefinitionException("@Bean method ${factoryClass.name}.${method.name} cannot be final")
            if (Modifier.isPrivate(mod))
                throw BeanDefinitionException("@Bean method ${factoryClass.name}.${method.name} cannot be private")
            val beanClass = method.returnType
            if (beanClass.isPrimitive)
                throw BeanDefinitionException("@Bean method ${factoryClass.name}.${method.name} cannot return primitive type")
            if (beanClass == Void.TYPE || beanClass == Void::class.java)
                throw BeanDefinitionException("@Bean method ${factoryClass.name}.${method.name} cannot return void")
            val info = BeanMetaInfo(
                getBeanName(method), beanClass, method.getOrder(), method.isPrimary(),
                factoryBeanName, method, bean.initMethod.ifEmpty { null }, bean.destroyMethod.ifEmpty { null },
                null, null
            )
            if (infos.put(info.beanName, info) != null) {
                throw BeanDefinitionException("Duplicate bean name: ${info.beanName}")
            }
            logger.atDebug().log("define bean: {}", info)
        }
    }

    /**
     * 注入属性
     */
    private fun injectProperties(info: BeanMetaInfo, clazz: Class<*>, bean: Any) {
        clazz.declaredFields.forEach {
            doInjectProperties(info, clazz, bean, it)
        }
        clazz.declaredMethods.forEach {
            doInjectProperties(info, clazz, bean, it)
        }
        val superClass = clazz.superclass
        if (superClass != null) {
            injectProperties(info, superClass, bean)
        }
    }

    private fun doInjectProperties(info: BeanMetaInfo, clazz: Class<*>, bean: Any, acc: AccessibleObject) {
        val valueAnno = acc.getAnnotation(Value::class.java)
        val autowiredAnno = acc.getAnnotation(Autowired::class.java)
        if (valueAnno == null && autowiredAnno == null) return

        val field: Field?
        val method: Method?
        acc.isAccessible = true
        when (acc) {
            is Field -> {
                checkFieldOrMethod(acc)
                field = acc
                method = null
            }

            is Method -> {
                checkFieldOrMethod(acc)
                if (acc.parameterCount != 1) {
                    throw BeanDefinitionException(
                        "Cannot inject a non-setter method $acc for bean '${info.beanName}': ${info.beanClass.name}"
                    )
                }
                method = acc
                field = null
            }

            else -> {
                throw AssertionError("Should not be here.")
            }
        }
        val accessibleName = field?.name ?: method!!.name
        val accessibleType = field?.type ?: method!!.parameterTypes.first()
        when {
            valueAnno != null -> {
                val propValue = propertyResolver.getRequiredProperty(valueAnno.value, accessibleType)
                if (field != null) {
                    logger.atDebug()
                        .log("Field injection: {}.{} = {}", info.beanClass.name, accessibleName, propValue)
                    field[bean] = propValue
                }
                if (method != null) {
                    logger.atDebug()
                        .log("Method injection: {}.{} ({})", info.beanClass.name, accessibleName, propValue)
                    method.invoke(bean, propValue)
                }
            }

            autowiredAnno != null -> {
                val name = autowiredAnno.name
                val required = autowiredAnno.value
                val depends = if (name.isEmpty()) tryGetBean(accessibleType) else tryGetBean(name, accessibleType)
                if (required && depends == null) {
                    throw DependencyException(
                        "Dependency bean not found when inject ${clazz.simpleName}.$accessibleName " +
                                "for bean '${info.beanName}': ${info.beanClass.name}"
                    )
                }
                if (depends != null) {
                    if (field != null) {
                        logger.atDebug()
                            .log("Field injection: {}.{} = {}", info.beanClass.name, accessibleName, depends)
                        field[bean] = depends
                    }
                    if (method != null) {
                        logger.atDebug()
                            .log("Method injection: {}.{} ({})", info.beanClass.name, accessibleName, depends)
                        method.invoke(bean, depends)
                    }
                }
            }

            else -> {
                throw BeanCreationException(
                    "Cannot specify both @Autowired and @Value when inject ${clazz.simpleName}.$accessibleName " +
                            "for bean '${info.beanName}': ${info.beanClass.name}"
                )
            }
        }
    }

    private fun checkFieldOrMethod(member: Member) {
        val mod = member.modifiers
        if (Modifier.isStatic(mod)) {
            throw BeanDefinitionException("Cannot inject static field or method: $member")
        }
        if (Modifier.isFinal(mod)) {
            if (member is Field) {
                throw BeanDefinitionException("Cannot inject final field: $member")
            }
            if (member is Method) {
                logger.warn("Inject final method should be careful because it may cause NPE when bean is proxied.")
            }
        }
    }

    override fun findBeanMetaInfos(type: Class<*>): List<BeanMetaInfo> {
        return infos.values.filter { type.isAssignableFrom(it.beanClass) }.sorted()
    }

    /**
     * 根据Type查找某个BeanDefinition，如果不存在返回null，如果存在多个返回@Primary标注的一个，如果有多个@Primary标注，
     * 或没有@Primary标注但找到多个，均抛出NoUniqueBeanDefinitionException
     */
    override fun findBeanMetaInfo(type: Class<*>): BeanMetaInfo? {
        val infos = findBeanMetaInfos(type)
        if (infos.isEmpty()) return null
        if (infos.size == 1) return infos.single()

        val primaryInfos = infos.filter { it.isPrimary }
        if (primaryInfos.size == 1) return primaryInfos.single()
        if (primaryInfos.isEmpty()) {
            throw NoUniqueBeanException("Multiple beans with type '$type' found, but no @Primary specified.")
        } else {
            throw NoUniqueBeanException("Multiple beans with type '$type' found, and multiple @Primary specified.")
        }
    }

    override fun findBeanMetaInfo(name: String): BeanMetaInfo? {
        return infos[name]
    }

    /**
     * 根据Name和Type查找BeanDefinition，如果Name不存在，返回null，如果Name存在，但Type不匹配，抛出异常。
     */
    override fun findBeanMetaInfo(name: String, requiredType: Class<*>): BeanMetaInfo? {
        val info = infos[name] ?: return null
        if (!requiredType.isAssignableFrom(info.beanClass)) {
            throw BeanTypeException(
                "Autowire required type '$requiredType' but bean '$name' has actual type '${info.beanClass}'."
            )
        }
        return info
    }

    /**
     * 创建一个Bean，然后使用BeanPostProcessor处理，但不进行字段和方法级别的注入。
     * 如果创建的Bean不是Configuration或BeanPostProcessor，则在构造方法中注入的依赖Bean会自动创建。
     */
    override fun createBean(info: BeanMetaInfo): Any {
        logger.atDebug().log("Try to create bean {} as early singleton: {}", info.beanName, info.beanClass.name)
        if (!creatingBeanNames.add(info.beanName)) {
            throw DependencyException("Circular dependency detected when create bean '${info.beanName}'")
        }
        val createFn = (if (info.factoryName == null) info.beanCtor else info.factoryMethod)!!
        val createFnParams = createFn.parameters
        val ctorAutowiredAnno = if (createFn is Constructor<*>) createFn.getAnnotation(Autowired::class.java) else null
        val args = arrayOfNulls<Any>(createFnParams.size)

        for (i in createFnParams.indices) {
            val param = createFnParams[i]
            val paramAnnos = createFn.parameterAnnotations[i].toList()
            val paramValueAnno = getAnnotation(paramAnnos, Value::class.java)
            var paramAutowiredAnno = getAnnotation(paramAnnos, Autowired::class.java)
            if (ctorAutowiredAnno != null && paramValueAnno == null && paramAutowiredAnno == null) {
                paramAutowiredAnno = Autowired()
            }

            if (info.isConfiguration && paramAutowiredAnno != null) {
                throw BeanCreationException(
                    "Cannot specify @Autowired when create @Configuration bean '${info.beanName}': ${info.beanClass.name}."
                )
            }
            if (info.isBeanPostProcessor && paramAutowiredAnno != null) {
                throw BeanCreationException(
                    "Cannot specify @Autowired when create BeanPostProcessor '${info.beanName}': ${info.beanClass.name}."
                )
            }
            if (paramValueAnno != null && paramAutowiredAnno != null) {
                throw BeanCreationException(
                    "Cannot specify both @Autowired and @Value when create bean '${info.beanName}': ${info.beanClass.name}."
                )
            }
            if (paramValueAnno == null && paramAutowiredAnno == null) {
                throw BeanCreationException(
                    "Must specify @Autowired or @Value when create bean '${info.beanName}': ${info.beanClass.name}."
                )
            }

            val type = param.type
            when {
                paramValueAnno != null -> {
                    args[i] = propertyResolver.getRequiredProperty(paramValueAnno.value, type)
                }

                paramAutowiredAnno != null -> {
                    val name = paramAutowiredAnno.name
                    val required = paramAutowiredAnno.value
                    val dependsOnInfo = if (name.isEmpty()) findBeanMetaInfo(type) else findBeanMetaInfo(name, type)
                    if (required && dependsOnInfo == null) {
                        throw BeanCreationException(
                            "Missing autowired bean with type '${type.name}' when create bean '${info.beanName}': ${info.beanClass.name}."
                        )
                    }
                    if (dependsOnInfo != null) {
                        // 获取依赖Bean:
                        var autowiredBeanInstance = dependsOnInfo.instance
                        if (autowiredBeanInstance == null && !info.isConfiguration && !info.isBeanPostProcessor) {
                            // 当前依赖Bean尚未初始化，递归调用初始化该依赖Bean:
                            autowiredBeanInstance = createBean(dependsOnInfo)
                        }
                        args[i] = autowiredBeanInstance
                    } else {
                        args[i] = null
                    }
                }

                else -> {
                    throw AssertionError("Should not be here.")
                }
            }
        }

        // 创建Bean实例:
        info.instance = try {
            if (info.factoryName == null)
            // 用构造方法创建:
                info.beanCtor!!.newInstance(*args)
            else
            // 用@Bean方法创建:
                info.factoryMethod!!.invoke(getBean(info.factoryName!!), *args)
        } catch (e: Exception) {
            throw BeanCreationException(
                "Exception when create bean '${info.beanName}': ${info.beanClass.name}", e
            )
        }

        postProcessors.forEach {
            val proceed = it.beforeInitialization(info.instance!!, info.beanName)
            if (info.instance !== proceed) {
                logger.atDebug().log("Bean {} was replaced by post processor {}", info.beanName, it.javaClass.name)
                info.instance = proceed
            }
        }
        return info.instance!!
    }

    override fun contains(name: String): Boolean {
        return infos.contains(name)
    }

    override fun <T> getBean(name: String): T {
        return tryGetBean(name) ?: throw NoSuchBeanException("No bean with name: $name")
    }

    /**
     * 通过Name和Type查找Bean，不存在抛出NoSuchBeanDefinitionException，
     * 存在但与Type不匹配抛出BeanNotOfRequiredTypeException
     */
    override fun <T> getBean(name: String, clazz: Class<T>): T {
        return tryGetBean(name, clazz) ?: throw NoSuchBeanException("No bean defined with type '$clazz'.")
    }

    override fun <T> getBean(clazz: Class<T>): T {
        return tryGetBean(clazz) ?: throw NoSuchBeanException("No bean defined with type '$clazz'.")
    }

    override fun <T> getBeans(clazz: Class<T>): List<T> {
        return findBeanMetaInfos(clazz).map { it.getRequiredInstance() as T }
    }

    override fun <T> tryGetBean(name: String): T? {
        val info = findBeanMetaInfo(name) ?: return null
        return info.getRequiredInstance() as T
    }

    override fun <T> tryGetBean(name: String, clazz: Class<T>): T? {
        val info = findBeanMetaInfo(name, clazz) ?: return null
        return info.getRequiredInstance() as T
    }

    override fun <T> tryGetBean(clazz: Class<T>): T? {
        val info = findBeanMetaInfo(clazz) ?: return null
        return info.getRequiredInstance() as T
    }

    override fun close() {
        logger.info("Closing {}...", this.javaClass.name)
        infos.values.forEach {
            invokeMethod(getProxiedInstance(it), it.destroyMethod, it.destroyMethodName)
        }
        infos.clear()
        logger.info("{} closed.", this.javaClass.name)
        ApplicationContextHolder.applicationContext = null
    }

    private fun getProxiedInstance(info: BeanMetaInfo): Any {
        var beanInstance = info.instance!!
        // 如果Proxy改变了原始Bean，又希望注入到原始Bean，则由BeanPostProcessor指定原始Bean:
        postProcessors.reversed().forEach {
            val restoredInstance = it.beforePropertySet(beanInstance, info.beanName)
            if (restoredInstance !== beanInstance) {
                logger.atDebug().log(
                    "BeanPostProcessor {} specified injection from {} to {}.",
                    it.javaClass.simpleName,
                    beanInstance.javaClass.simpleName,
                    restoredInstance.javaClass.simpleName
                )
                beanInstance = restoredInstance
            }
        }
        return beanInstance
    }

    private fun invokeMethod(beanInstance: Any, method: Method?, methodName: String?) {
        if (method != null) {
            try {
                method.invoke(beanInstance)
            } catch (e: ReflectiveOperationException) {
                throw BeanCreationException("Method $method invocation failed.", e)
            }
        } else if (methodName != null) {
            val namedMethod = getNamedMethod(beanInstance.javaClass, methodName)
            namedMethod.isAccessible = true
            try {
                namedMethod.invoke(beanInstance)
            } catch (e: ReflectiveOperationException) {
                throw BeanCreationException("Named method $namedMethod invocation failed.", e)
            }
        }
    }
}

object ApplicationContextHolder {
    var applicationContext: ApplicationContext? = null
    val requiredApplicationContext: ApplicationContext
        get() = Objects.requireNonNull(applicationContext, "ApplicationContext is not set.")!!
}

interface ApplicationContext : AutoCloseable {
    fun contains(name: String): Boolean

    fun <T> getBean(name: String): T

    fun <T> getBean(name: String, clazz: Class<T>): T

    fun <T> getBean(clazz: Class<T>): T

    fun <T> getBeans(clazz: Class<T>): List<T>

    fun <T> tryGetBean(name: String): T?

    fun <T> tryGetBean(name: String, clazz: Class<T>): T?

    fun <T> tryGetBean(clazz: Class<T>): T?

    override fun close()
}

interface ConfigurableApplicationContext : ApplicationContext {
    fun findBeanMetaInfos(type: Class<*>): List<BeanMetaInfo>

    fun findBeanMetaInfo(type: Class<*>): BeanMetaInfo?

    fun findBeanMetaInfo(name: String): BeanMetaInfo?

    fun findBeanMetaInfo(name: String, requiredType: Class<*>): BeanMetaInfo?

    fun createBean(info: BeanMetaInfo): Any
}