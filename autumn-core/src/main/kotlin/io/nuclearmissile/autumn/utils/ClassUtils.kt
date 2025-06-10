package io.nuclearmissile.autumn.utils

import io.nuclearmissile.autumn.annotation.Component
import io.nuclearmissile.autumn.aop.Invocation
import io.nuclearmissile.autumn.aop.InvocationChain
import io.nuclearmissile.autumn.exception.BeanDefinitionException
import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy
import net.bytebuddy.implementation.InvocationHandlerAdapter
import net.bytebuddy.implementation.attribute.MethodAttributeAppender
import net.bytebuddy.matcher.ElementMatchers
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

object ClassUtils {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val byteBuddy = ByteBuddy()
    private val lookup = MethodHandles.lookup()
    private val methodHandleCache = ConcurrentHashMap<Method, MethodHandle>()

    fun <T> createProxy(bean: T, aopHandlers: List<Invocation>): T {
        if (aopHandlers.isEmpty()) return bean
        val targetClass = bean!!.javaClass
        logger.atDebug().log("create proxy for bean {}", targetClass.name)
        val proxyClass = byteBuddy.subclass(targetClass, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR)
            .method(ElementMatchers.isPublic())
            .intercept(InvocationHandlerAdapter.of { _, method, args ->
                val methodHandle = methodHandleCache.computeIfAbsent(method) { lookup.unreflect(method) }
                InvocationChain(aopHandlers, methodHandle).invokeChain(bean, method, args)
            })
            .attribute(MethodAttributeAppender.ForInstrumentedMethod.EXCLUDING_RECEIVER)
            .make().load(targetClass.classLoader).loaded
        return proxyClass.getConstructor().newInstance() as T
    }

    /**
     * 递归查找Annotation
     *
     * 示例：Annotation A可以直接标注在Class定义:
     *
     * @A
     * public class Hello {}
     *
     * 或者Annotation B标注了A，Class标注了B:
     *
     * @A
     * public @interface B {}
     *
     * @B
     * public class Hello {}
     *
     */
    fun <A : Annotation> Class<*>.findNestedAnnotation(annoClass: Class<A>): A? {
        var res = getAnnotation(annoClass)
        for (anno in annotations) {
            val annoType = anno.annotationClass.java
            if (annoType.packageName.startsWith("java") || annoType.packageName.startsWith("kotlin"))
                continue
            val found = annoType.findNestedAnnotation(annoClass)
            if (found != null) {
                if (res != null) {
                    throw BeanDefinitionException("Duplicate @${annoClass.simpleName} found on class $simpleName")
                }
                res = found
            }
        }
        return res
    }

    /**
     * Get bean name by:
     *
     *
     * @Component
     * public class Hello {}
     *
     */
    fun Class<*>.getBeanName(): String {
        // 查找@Component:
        var name = findNestedAnnotation(Component::class.java)?.value ?: ""
        if (name.isEmpty()) {
            for (anno in annotations) {
                val annoType = anno.annotationClass.java
                if (annoType.findNestedAnnotation(Component::class.java) != null) {
                    try {
                        val value = annoType.getMethod("value").invoke(anno) as String
                        if (value.isNotEmpty()) {
                            if (name.isNotEmpty()) {
                                throw BeanDefinitionException("Duplicate ${annoType.simpleName}.value found on class $simpleName")
                            }
                            name = value
                        }
                    } catch (e: Throwable) {
                        when (e) {
                            is ReflectiveOperationException, is ClassCastException ->
                                throw BeanDefinitionException("Get ${annoType.simpleName}.value failed", e)

                            else -> throw e
                        }
                    }
                }
            }
        }
        return name.ifEmpty { simpleName.replaceFirstChar { it.lowercase() } }
    }

    fun scanClassNames(basePackagePaths: List<String>): Set<String> {
        return basePackagePaths.flatMap {
            ResourceResolver(it).scanResources { res ->
                if (res.name.endsWith(".class"))
                    res.name.removeSuffix(".class").replace("/", ".").replace("\\", ".")
                else null
            }
        }.toSet()
    }

    fun <T> createInstance(className: String): T {
        return createInstance(Class.forName(className, true, Thread.currentThread().contextClassLoader)) as T
    }

    fun <T> createInstance(clazz: Class<T>): T {
        return clazz.getConstructor().newInstance()
    }

    fun <T> withClassLoader(classLoader: ClassLoader, supplier: Supplier<T>): T {
        val original = Thread.currentThread().contextClassLoader
        return try {
            Thread.currentThread().contextClassLoader = classLoader
            supplier.get()
        } finally {
            Thread.currentThread().contextClassLoader = original
        }
    }

    fun InvocationTargetException.extractTarget(): Throwable {
        return if (targetException!! !is InvocationTargetException)
            targetException
        else
            (targetException as InvocationTargetException).extractTarget()
    }

    fun findClosestMatchingType(target: Class<*>, candidates: Collection<Class<*>>): Class<*>? {
        if (candidates.contains(target))
            return target

        val queue = LinkedList<Class<*>>()
        queue.offer(target)

        while (queue.isNotEmpty()) {
            val curr = queue.poll()
            val superClass = curr.superclass

            if (superClass != null && candidates.contains(superClass))
                return superClass
            if (superClass != null)
                queue.offer(superClass)

            for (iface in curr.interfaces) {
                if (candidates.contains(iface))
                    return iface
                queue.offer(iface)
            }
        }

        return null
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> String.toPrimitive(clazz: Class<T>): T? {
        return when (clazz) {
            java.lang.String::class.java, String::class.java -> this
            java.lang.Boolean::class.java, Boolean::class.java -> toBoolean()
            java.lang.Integer::class.java, Int::class.java -> toInt()
            java.lang.Long::class.java, Long::class.java -> toLong()
            java.lang.Byte::class.java, Byte::class.java -> toByte()
            java.lang.Short::class.java, Short::class.java -> toShort()
            java.lang.Float::class.java, Float::class.java -> toFloat()
            java.lang.Double::class.java, Double::class.java -> toDouble()
            else -> null
        } as T?
    }
}
