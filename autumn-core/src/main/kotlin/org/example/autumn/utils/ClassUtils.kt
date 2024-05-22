package org.example.autumn.utils

import org.example.autumn.annotation.Component
import org.example.autumn.exception.BeanDefinitionException
import org.example.autumn.resolver.ResourceResolver
import java.lang.reflect.InvocationTargetException
import java.util.function.Supplier

object ClassUtils {
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
}
