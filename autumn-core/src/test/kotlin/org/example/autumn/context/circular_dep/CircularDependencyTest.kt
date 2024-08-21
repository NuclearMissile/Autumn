package org.example.autumn.context.circular_dep

import org.example.autumn.annotation.Autowired
import org.example.autumn.annotation.Component
import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.exception.DependencyException
import org.example.autumn.resolver.Config
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.Test

class CircularDependencyTestConfiguration

@Component
class A(@Autowired private val b: B)

@Component
class B(@Autowired private val a: A)

class CircularDependencyTest {
    @Test
    fun testCircularDependency() {
        assertThrows<DependencyException> {
            AnnotationConfigApplicationContext(CircularDependencyTestConfiguration::class.java, Config(Properties()))
        }
    }
}
