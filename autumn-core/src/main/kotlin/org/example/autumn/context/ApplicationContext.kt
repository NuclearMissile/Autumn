package org.example.autumn.context

import org.example.autumn.annotation.*
import org.example.autumn.exception.*
import org.example.autumn.resolver.PropertyResolver
import org.example.autumn.utils.ClassUtils.findNestedAnnotation
import org.example.autumn.utils.ClassUtils.getBeanName
import org.example.autumn.utils.ClassUtils.scanClassNames
import org.slf4j.LoggerFactory
import java.lang.reflect.*

object ApplicationContextHolder {
    internal var instance: ApplicationContext? = null
    val required: ApplicationContext
        get() = requireNotNull(instance) { "ApplicationContext is not set." }
}

class AnnotationConfigApplicationContext(
    configClass: Class<*>, private val config: PropertyResolver,
) : ApplicationContext {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val infoMap: MutableMap<String, BeanMetaInfo>
    private val sortedInfos: List<BeanMetaInfo>
    private val postProcessors = mutableListOf<BeanPostProcessor>()
    private val creatingBeanNames = mutableSetOf<String>()

    override val managedClassNames = scanClassNamesOnConfigClass(configClass)

    init {
        // register this to app context holder
        ApplicationContextHolder.instance = this
        infoMap = createBeanMetaInfos(managedClassNames)
        sortedInfos = infoMap.values.sorted()
        // init @Configuration beans
        sortedInfos.filter { it.isConfiguration }.forEach(::createEarlySingleton)
        // init BeanPostProcessor beans
        postProcessors += sortedInfos.filter { it.isBeanPostProcessor }.map {
            createEarlySingleton(it) as BeanPostProcessor
        }
        // init naive beans
        sortedInfos.forEach { it.instance ?: createEarlySingleton(it) }
        // inject depends via field or setter
        sortedInfos.forEach {
            try {
                injectProperties(it, it.beanClass, getOriginalInstance(it))
            } catch (e: ReflectiveOperationException) {
                throw BeanCreationException("Error while injectBean for $it", e)
            }
        }
        // call init method
        sortedInfos.forEach { info ->
            invokeMethod(getOriginalInstance(info), info.initMethod, info.initMethodName)
            postProcessors.forEach { postProcessor ->
                val processed = postProcessor.afterInitialization(info.requiredInstance, info.beanName)
                if (processed !== info.requiredInstance) {
                    logger.atDebug().log(
                        "BeanPostProcessor {} return different bean from {} to {}.",
                        postProcessor.javaClass.name, info.requiredInstance.javaClass.name, processed.javaClass.name
                    )
                    info.instance = processed
                }
            }
        }
        if (logger.isDebugEnabled) {
            sortedInfos.forEach { logger.debug("bean initialized: $it") }
        }
    }

    private fun scanClassNamesOnConfigClass(configClass: Class<*>): Set<String> {
        val scanAnno = configClass.getAnnotation(ComponentScan::class.java)
        val scanPackages = if (scanAnno == null || scanAnno.value.isEmpty())
            listOf(configClass.packageName) else scanAnno.value.toList()
        logger.atInfo().log("component scan in packages: {}", scanPackages.joinToString())

        val classNameSet = scanClassNames(scanPackages).also {
            logger.atDebug().log("class found by component scan: {}", it)
        }.toMutableSet()

        configClass.getAnnotation(Import::class.java)?.value?.forEach {
            val importClassName = it.java.name
            if (classNameSet.contains(importClassName)) {
                logger.warn("ignore import: $importClassName, already imported.")
            } else {
                logger.debug("class found by import: {}", importClassName)
                classNameSet.add(importClassName)
            }
        }
        return classNameSet
    }

    private fun createBeanMetaInfos(classNames: Collection<String>): MutableMap<String, BeanMetaInfo> {
        /**
         * Get non-arg method by @PostConstruct or @PreDestroy. Not search in super class.
         */
        fun Class<*>.findLifecycleMethod(annoClass: Class<out Annotation>): Method? {
            // try get declared method:
            val ms = declaredMethods.filter { it.isAnnotationPresent(annoClass) }
            require(ms.size <= 1) {
                throw BeanDefinitionException(
                    "Multiple methods with @${annoClass.simpleName} found in class: $name"
                )
            }
            require(ms.isEmpty() || ms[0].parameterCount == 0) {
                throw BeanDefinitionException(
                    "Method '${ms[0].name}' with @${annoClass.simpleName} must not have argument: $name"
                )
            }
            return ms.firstOrNull()
        }

        val infoMap = mutableMapOf<String, BeanMetaInfo>()
        for (className in classNames) {
            // 获取Class:
            val clazz = try {
                Class.forName(className, true, Thread.currentThread().contextClassLoader)
            } catch (e: ClassNotFoundException) {
                throw BeanCreationException("Class not found for name: $className", e)
            }
            if (clazz.isAnnotation || clazz.isEnum || clazz.isInterface || clazz.isRecord) {
                continue
            }

            // 是否标注@Component?
            clazz.findNestedAnnotation(Component::class.java) ?: continue
            logger.atDebug().log("found component: {}", clazz.name)
            val mod = clazz.modifiers
            if (Modifier.isAbstract(mod)) {
                throw BeanDefinitionException("@Component class ${clazz.name} must not be abstract.")
            }
            if (Modifier.isPrivate(mod)) {
                throw BeanDefinitionException("@Component class ${clazz.name} must not be private.")
            }

            val beanName = clazz.getBeanName()
            val info = BeanMetaInfo(
                beanName, clazz, clazz.getOrder(), clazz.isPrimary(), clazz.beanCtor(),
                clazz.findLifecycleMethod(PostConstruct::class.java), clazz.findLifecycleMethod(PreDestroy::class.java)
            )
            if (infoMap.put(info.beanName, info) != null) {
                throw BeanDefinitionException("Duplicate bean name: ${info.beanName}")
            }
            logger.atDebug().log("define bean via @Component: {}", info)

            // handle factory method
            clazz.getAnnotation(Configuration::class.java) ?: continue
            if (BeanPostProcessor::class.java.isAssignableFrom(clazz)) {
                throw BeanDefinitionException("@Configuration class '${clazz.name}' cannot be BeanPostProcessor.")
            }
            scanFactoryMethods(beanName, clazz, infoMap)
        }
        return infoMap
    }

    private fun Class<*>.getOrder() = getAnnotation(Order::class.java)?.value ?: Int.MAX_VALUE
    private fun Method.getOrder() = getAnnotation(Order::class.java)?.value ?: Int.MAX_VALUE
    private fun Class<*>.isPrimary() = isAnnotationPresent(Primary::class.java)
    private fun Method.isPrimary() = isAnnotationPresent(Primary::class.java)
    private fun Class<*>.beanCtor() = run {
        val ctors = declaredConstructors.sortedByDescending { it.parameterCount }
        ctors.firstOrNull { it.isAnnotationPresent(Autowired::class.java) } ?: ctors.firstOrNull {
            it.parameters.all { p ->
                p.isAnnotationPresent(Autowired::class.java) || p.isAnnotationPresent(Value::class.java)
            }
        } ?: throw BeanDefinitionException("No valid bean constructor found in class: $name.")
    }

    /**
     * Scan factory method that annotated with @Bean:
     */
    private fun scanFactoryMethods(
        factoryBeanName: String, factoryClass: Class<*>, infos: MutableMap<String, BeanMetaInfo>,
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
            val beanName = method.getAnnotation(Bean::class.java)!!.value.ifEmpty { method.name }
            val info = BeanMetaInfo(
                beanName, beanClass, method.getOrder(), method.isPrimary(), factoryBeanName,
                method, bean.initMethod.ifEmpty { null }, bean.destroyMethod.ifEmpty { null }
            )
            if (infos.put(info.beanName, info) != null) {
                throw BeanDefinitionException("Duplicate bean name: ${info.beanName}")
            }
            logger.atDebug().log("define bean via factory method: {}", info)
        }
    }

    /**
     * 注入属性
     */
    private fun injectProperties(info: BeanMetaInfo, clazz: Class<*>, bean: Any) {
        fun Member.checkModifier() {
            when {
                Modifier.isStatic(modifiers) -> {
                    throw BeanDefinitionException("Cannot inject static field or method: $this")
                }

                Modifier.isFinal(modifiers) -> {
                    if (this is Field) {
                        throw BeanDefinitionException("Cannot inject final field: $this")
                    }
                    if (this is Method) {
                        logger.warn("Inject final method should be careful because it may cause NPE if that bean is proxied.")
                    }
                }
            }
        }

        fun doInject(info: BeanMetaInfo, clazz: Class<*>, bean: Any, acc: AccessibleObject) {
            val valueAnno = acc.getAnnotation(Value::class.java)
            val autowiredAnno = acc.getAnnotation(Autowired::class.java)
            if (valueAnno == null && autowiredAnno == null) return

            acc.isAccessible = true
            val field: Field?
            val method: Method?
            when (acc) {
                is Field -> {
                    acc.checkModifier()
                    field = acc
                    method = null
                }

                is Method -> {
                    if (acc.parameterCount != 1) {
                        throw BeanDefinitionException(
                            "Cannot inject a non-setter method $acc for bean '${info.beanName}': ${info.beanClass.name}"
                        )
                    }
                    acc.checkModifier()
                    method = acc
                    field = null
                }

                else -> {
                    throw AssertionError("Should not be here.")
                }
            }
            val accessibleName = field?.name ?: method!!.name
            val accessibleType = field?.type ?: method!!.parameterTypes.first()
            @Suppress("KotlinConstantConditions")
            when {
                valueAnno != null -> {
                    val propValue = config.getRequired(valueAnno.value, accessibleType)
                    if (field != null) {
                        logger.atDebug().log(
                            "Field injection by @Value: {}.{} = {}", info.beanClass.name, accessibleName, propValue
                        )
                        field[bean] = propValue
                    }
                    if (method != null) {
                        logger.atDebug().log(
                            "Method injection by @Value: {}.{} ({})", info.beanClass.name, accessibleName, propValue
                        )
                        method.invoke(bean, propValue)
                    }
                }

                autowiredAnno != null -> {
                    val name = autowiredAnno.name
                    val required = autowiredAnno.value
                    val depends = if (name.isEmpty()) tryGetBean(accessibleType) else tryGetBean(name, accessibleType)
                    if (required && depends == null) {
                        throw DependencyException(
                            "Dependency bean not found when inject ${clazz.simpleName}.$accessibleName for bean '${info.beanName}': ${info.beanClass.name}"
                        )
                    }
                    if (depends != null) {
                        if (field != null) {
                            logger.atDebug().log(
                                "Field injection by @Autowired: {}.{} = {}",
                                info.beanClass.name, accessibleName, depends
                            )
                            field[bean] = depends
                        }
                        if (method != null) {
                            logger.atDebug().log(
                                "Method injection by @AutoWired: {}.{} ({})",
                                info.beanClass.name, accessibleName, depends
                            )
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

        clazz.declaredFields.forEach {
            doInject(info, clazz, bean, it)
        }
        clazz.declaredMethods.forEach {
            doInject(info, clazz, bean, it)
        }
        val superClass = clazz.superclass
        if (superClass != null) {
            injectProperties(info, superClass, bean)
        }
    }

    override fun findBeanMetaInfos(type: Class<*>): List<BeanMetaInfo> {
        return sortedInfos.filter { type.isAssignableFrom(it.beanClass) }
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
        return infoMap[name]
    }

    /**
     * 根据Name和Type查找BeanDefinition，如果Name不存在，返回null，如果Name存在，但Type不匹配，抛出异常。
     */
    override fun findBeanMetaInfo(name: String, requiredType: Class<*>): BeanMetaInfo? {
        val info = infoMap[name] ?: return null
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
    override fun createEarlySingleton(info: BeanMetaInfo): Any {
        logger.atDebug().log("Try to create bean {} as early singleton: {}", info.beanName, info.beanClass.name)
        if (!creatingBeanNames.add(info.beanName)) {
            throw DependencyException("Circular dependency detected when create bean '${info.beanName}'")
        }
        val createFn = (info.beanCtor ?: info.factoryMethod)!!
        val createFnParams = createFn.parameters
        val ctorAutowiredAnno = if (createFn is Constructor<*>) createFn.getAnnotation(Autowired::class.java) else null
        val args = arrayOfNulls<Any>(createFnParams.size)

        for (i in createFnParams.indices) {
            val param = createFnParams[i]
            val paramAnnos = createFn.parameterAnnotations[i].toList()
            val paramValueAnno = paramAnnos.firstOrNull { Value::class.java.isInstance(it) } as Value?
            var paramAutowiredAnno = paramAnnos.firstOrNull { Autowired::class.java.isInstance(it) } as Autowired?
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
                    args[i] = config.getRequired(paramValueAnno.value, type)
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
                        @Suppress("KotlinConstantConditions")
                        if (autowiredBeanInstance == null && !info.isConfiguration && !info.isBeanPostProcessor) {
                            // 当前依赖Bean尚未初始化，递归调用初始化该依赖Bean:
                            autowiredBeanInstance = createEarlySingleton(dependsOnInfo)
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
            when {
                info.beanCtor != null -> info.beanCtor!!.newInstance(*args)
                info.factoryMethod != null -> info.factoryMethod!!.invoke(getBean(info.factoryName!!), *args)
                else -> BeanDefinitionException("cannot instantiate $info")
            }
        } catch (e: Exception) {
            throw BeanCreationException(
                "Exception when create bean '${info.beanName}': ${info.beanClass.name}", e
            )
        }

        postProcessors.forEach {
            val proceed = it.beforeInitialization(info.requiredInstance, info.beanName)
            if (info.instance !== proceed) {
                logger.atDebug().log("Bean {} was replaced by post processor {}", info.beanName, it.javaClass.name)
                info.instance = proceed
            }
        }
        return info.requiredInstance
    }

    override fun getConfig(): PropertyResolver {
        return config
    }

    override fun contains(name: String): Boolean {
        return infoMap.contains(name)
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

    @Suppress("UNCHECKED_CAST")
    override fun <T> getBeans(clazz: Class<T>): List<T> {
        return findBeanMetaInfos(clazz).map { it.requiredInstance as T }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> tryGetBean(name: String): T? {
        val info = findBeanMetaInfo(name) ?: return null
        return info.requiredInstance as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> tryGetBean(name: String, clazz: Class<T>): T? {
        val info = findBeanMetaInfo(name, clazz) ?: return null
        return info.requiredInstance as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> tryGetBean(clazz: Class<T>): T? {
        val info = findBeanMetaInfo(clazz) ?: return null
        return info.requiredInstance as T
    }

    override fun close() {
        logger.info("Closing {}...", this.javaClass.name)
        sortedInfos.forEach {
            invokeMethod(getOriginalInstance(it), it.destroyMethod, it.destroyMethodName)
        }
        infoMap.clear()
        logger.info("{} closed.", this.javaClass.name)
        ApplicationContextHolder.instance = null
    }

    private fun getOriginalInstance(info: BeanMetaInfo): Any {
        var ret = info.requiredInstance
        // 如果Proxy改变了原始Bean，又希望注入到原始Bean，则由BeanPostProcessor指定原始Bean:
        postProcessors.reversed().forEach {
            val restoredInstance = it.beforePropertySet(ret, info.beanName)
            if (restoredInstance !== ret) {
                ret = restoredInstance
            }
        }
        if (info.requiredInstance !== ret) {
            logger.atDebug().log(
                "Get original bean instance for {}, original: {}.",
                info.requiredInstance.javaClass.simpleName,
                ret.javaClass.simpleName
            )
        }
        return ret
    }

    private fun invokeMethod(beanInstance: Any, method: Method?, methodName: String?) {
        if (method != null) {
            try {
                method.invoke(beanInstance)
            } catch (e: ReflectiveOperationException) {
                throw BeanCreationException("Method $method invocation failed.", e)
            }
        } else if (methodName != null) {
            val namedMethod = try {
                beanInstance.javaClass.getDeclaredMethod(methodName)
            } catch (e: ReflectiveOperationException) {
                throw BeanDefinitionException("Method '$methodName' not found in class: ${beanInstance.javaClass.name}")
            }
            namedMethod.isAccessible = true
            try {
                namedMethod.invoke(beanInstance)
            } catch (e: ReflectiveOperationException) {
                throw BeanCreationException("Named method $namedMethod invocation failed.", e)
            }
        }
    }
}

interface ApplicationContext : AutoCloseable {
    val managedClassNames: Set<String>

    fun contains(name: String): Boolean

    fun <T> getBean(name: String): T

    fun <T> getBean(name: String, clazz: Class<T>): T

    fun <T> getBean(clazz: Class<T>): T

    fun <T> getBeans(clazz: Class<T>): List<T>

    fun <T> tryGetBean(name: String): T?

    fun <T> tryGetBean(name: String, clazz: Class<T>): T?

    fun <T> tryGetBean(clazz: Class<T>): T?

    fun findBeanMetaInfos(type: Class<*>): List<BeanMetaInfo>

    fun findBeanMetaInfo(type: Class<*>): BeanMetaInfo?

    fun findBeanMetaInfo(name: String): BeanMetaInfo?

    fun findBeanMetaInfo(name: String, requiredType: Class<*>): BeanMetaInfo?

    fun createEarlySingleton(info: BeanMetaInfo): Any

    fun getConfig(): PropertyResolver

    override fun close()
}