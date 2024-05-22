package org.example.autumn.utils

import org.example.autumn.annotation.Component
import org.example.autumn.exception.BeanDefinitionException
import org.example.autumn.utils.ClassUtils.findNestedAnnotation
import org.example.autumn.utils.ClassUtils.getBeanName
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Component
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Component1(val value: String = "")

@Component1
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Component2(val value: String = "")

@Component2
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Component3(val value: Int = 0)

@Component2
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Component4

@Component
class TestClass0

@Component("c0")
class TestClass00

class TestClass000

@Component1("c1")
class TestClass1

@Component1
class TestClass11

@Component2("c2")
class TestClass2

@Component("c")
@Component2("c22")
class TestClass22

@Component3
class TestClass3

@Component4
class TestClass4

class ClassUtilsTest {
    @Test
    fun testFindNestedAnnotation() {
        assertEquals(Component(), TestClass0::class.java.findNestedAnnotation(Component::class.java))
        assertNull(TestClass000::class.java.findNestedAnnotation(Component::class.java))

        assertEquals(Component(), TestClass1::class.java.findNestedAnnotation(Component::class.java))
        assertEquals(Component(), TestClass2::class.java.findNestedAnnotation(Component::class.java))

        assertThrows<BeanDefinitionException> { TestClass22::class.java.findNestedAnnotation(Component::class.java) }
    }

    @Test
    fun testGetBeanName() {
        assertEquals("testClass0", TestClass0::class.java.getBeanName())
        assertEquals("c0", TestClass00::class.java.getBeanName())
        assertEquals("testClass000", TestClass000::class.java.getBeanName())
        assertEquals("c1", TestClass1::class.java.getBeanName())
        assertEquals("testClass11", TestClass11::class.java.getBeanName())
        assertEquals("c2", TestClass2::class.java.getBeanName())
        assertThrows<BeanDefinitionException> { TestClass22::class.java.getBeanName() }
        assertThrows<BeanDefinitionException> { TestClass3::class.java.getBeanName() }
        assertThrows<BeanDefinitionException> { TestClass4::class.java.getBeanName() }
    }
}