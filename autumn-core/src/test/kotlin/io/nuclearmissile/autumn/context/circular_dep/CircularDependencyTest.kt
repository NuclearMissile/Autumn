package io.nuclearmissile.autumn.context.circular_dep

import io.nuclearmissile.autumn.annotation.Autowired
import io.nuclearmissile.autumn.annotation.Component
import io.nuclearmissile.autumn.context.AnnotationApplicationContext
import io.nuclearmissile.autumn.exception.DependencyException
import io.nuclearmissile.autumn.utils.ConfigProperties
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
            AnnotationApplicationContext(
                CircularDependencyTestConfiguration::class.java,
                ConfigProperties(Properties())
            )
        }
    }
}
