package org.example.autumn.context.circular_dep_workaround

import org.example.autumn.annotation.Autowired
import org.example.autumn.annotation.Component
import org.example.autumn.annotation.ComponentScan
import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.resolver.Config
import java.util.*
import kotlin.test.Test

@ComponentScan
class CircularDependencyWorkaroundTestConfiguration

@Component
class A(@Autowired private val b: B)

@Component
class B {
    private lateinit var a: A
}

class CircularDependencyWorkaroundTest {
    @Test
    fun testCircularDependencyWorkaround() {
        AnnotationConfigApplicationContext(
            CircularDependencyWorkaroundTestConfiguration::class.java, Config(Properties())
        ).close()
    }
}
