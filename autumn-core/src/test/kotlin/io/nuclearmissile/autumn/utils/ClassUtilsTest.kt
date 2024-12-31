package io.nuclearmissile.autumn.utils

import io.nuclearmissile.autumn.annotation.Component
import io.nuclearmissile.autumn.exception.BeanDefinitionException
import io.nuclearmissile.autumn.utils.ClassUtils.findClosestMatchingType
import io.nuclearmissile.autumn.utils.ClassUtils.findNestedAnnotation
import io.nuclearmissile.autumn.utils.ClassUtils.getBeanName
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

open class TestException1 : Exception()

open class TestException2 : TestException1()

open class TestException3 : TestException2()

class TestException4 : Exception()

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

    @Test
    fun testDirectMatch() {
        val candidates = listOf(Runnable::class.java, Comparable::class.java, Cloneable::class.java)
        val result = findClosestMatchingType(Runnable::class.java, candidates)
        assertEquals(Runnable::class.java, result)
    }

    @Test
    fun testSuperclassMatch() {
        val candidates = listOf(AbstractList::class.java, Collection::class.java)
        val result = findClosestMatchingType(ArrayList::class.java, candidates)
        assertEquals(Collection::class.java, result)
    }

    @Test
    fun testInterfaceMatch() {
        val candidates = listOf(Runnable::class.java, List::class.java)
        val result = findClosestMatchingType(ArrayList::class.java, candidates)
        assertEquals(List::class.java, result)
    }

    @Test
    fun testNoMatch() {
        val candidates = listOf(Runnable::class.java, Thread::class.java)
        val result = findClosestMatchingType(String::class.java, candidates)
        assertNull(result)
    }

    @Test
    fun testMultipleMatches() {
        val candidates = listOf(Collection::class.java, List::class.java, AbstractList::class.java)
        val result = findClosestMatchingType(ArrayList::class.java, candidates)
        assertEquals(List::class.java, result)
    }

    @Test
    fun testPrimitiveType() {
        val candidates = listOf(Any::class.java, Number::class.java, Int::class.java)
        val result = findClosestMatchingType(Int::class.java, candidates)
        assertEquals(Int::class.java, result)
    }

    @Test
    fun testException() {
        val candidates = listOf(Exception::class.java, TestException1::class.java)
        assertEquals(Exception::class.java, findClosestMatchingType(Exception::class.java, candidates))
        assertEquals(Exception::class.java, findClosestMatchingType(TestException4::class.java, candidates))
        assertEquals(TestException1::class.java, findClosestMatchingType(TestException1::class.java, candidates))
        assertEquals(TestException1::class.java, findClosestMatchingType(TestException3::class.java, candidates))
    }
}