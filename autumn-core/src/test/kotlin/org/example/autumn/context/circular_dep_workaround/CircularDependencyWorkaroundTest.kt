package org.example.autumn.context.circular_dep_workaround

import org.example.autumn.annotation.Autowired
import org.example.autumn.annotation.Component
import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.resolver.Config
import java.util.*
import kotlin.test.Test

class CircularDependencyWorkaroundTestConfiguration

@Component
class A(@Autowired private val b: B)

@Component
class B {
    @Autowired
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
