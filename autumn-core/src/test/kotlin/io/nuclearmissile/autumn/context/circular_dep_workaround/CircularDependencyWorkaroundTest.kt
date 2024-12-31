package io.nuclearmissile.autumn.context.circular_dep_workaround

import io.nuclearmissile.autumn.annotation.Autowired
import io.nuclearmissile.autumn.annotation.Component
import io.nuclearmissile.autumn.context.AnnotationApplicationContext
import io.nuclearmissile.autumn.utils.ConfigProperties
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
        AnnotationApplicationContext(
            CircularDependencyWorkaroundTestConfiguration::class.java, ConfigProperties(Properties())
        ).close()
    }
}
