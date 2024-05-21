package org.example.autumn.utils

import org.example.autumn.annotation.Component
import org.example.autumn.exception.BeanDefinitionException
import org.example.autumn.resolver.ResourceResolver
import java.lang.reflect.InvocationTargetException

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
    fun <A : Annotation> Class<*>.findAnnotation(annoClass: Class<A>): A? {
        var res = getAnnotation(annoClass)
        for (anno in annotations) {
            val annoType = anno.annotationClass.java
            if (annoType.packageName.startsWith("java") || annoType.packageName.startsWith("kotlin"))
                continue
            val found = annoType.findAnnotation(annoClass)
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
        var name = ""
        val component = getAnnotation(Component::class.java)
        if (component != null) {
            name = component.value
        } else {
            // 未找到@Component，继续在其他注解中查找@Component:
            for (anno in annotations.sortedBy { it.javaClass.simpleName }) {
                val annoClass = anno.annotationClass.java
                if (annoClass.findAnnotation(Component::class.java) != null) {
                    try {
                        name = annoClass.getMethod("value").invoke(anno) as String
                        break
                    } catch (e: ReflectiveOperationException) {
                        throw BeanDefinitionException("Cannot get annotation value.", e)
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

    fun <T> withClassLoader(classLoader: ClassLoader, func: () -> T): T {
        val original = Thread.currentThread().contextClassLoader
        return try {
            Thread.currentThread().contextClassLoader = classLoader
            func.invoke()
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
