package org.example.autumn.context

import org.example.autumn.exception.BeanTypeException
import org.example.autumn.exception.NoSuchBeanException
import org.example.autumn.exception.NoUniqueBeanException
import org.example.autumn.utils.ConfigProperties
import org.example.imported.LocalDateConfiguration
import org.example.imported.ZonedDateConfiguration
import org.example.scan.*
import org.example.scan.sub1.Sub1Bean
import org.example.scan.sub1.sub2.Sub2Bean
import org.example.scan.sub1.sub2.sub3.Sub3Bean
import org.junit.jupiter.api.assertThrows
import java.time.*
import kotlin.test.*

class AnnotationApplicationContextTest {
    @Test
    fun testCustomAnnotation() {
        AnnotationApplicationContext(ScanConfiguration::class.java, config).use { ctx ->
            assertNotNull(ctx.tryGetUniqueBean(CustomAnnotationBean::class.java))
            assertNotNull(ctx.tryGetBean("customAnnotation"))
        }
    }

    @Test
    fun testInitMethod() {
        AnnotationApplicationContext(ScanConfiguration::class.java, config).use { ctx ->
            // test @PostConstruct:
            val bean1 = ctx.getUniqueBean(AnnotationInitBean::class.java)
            val bean2 = ctx.getUniqueBean(SpecifyInitBean::class.java)
            assertEquals("Scan App / v1.0", bean1.appName)
            assertEquals("Scan App / v1.0", bean2.appName)
        }
    }

    @Test
    fun testImport() {
        AnnotationApplicationContext(ScanConfiguration::class.java, config).use { ctx ->
            assertNotNull(ctx.tryGetUniqueBean(LocalDateConfiguration::class.java))
            assertNotNull(ctx.tryGetBean("startLocalDate"))
            assertNotNull(ctx.tryGetBean("startLocalDateTime"))
            assertNotNull(ctx.tryGetUniqueBean(ZonedDateConfiguration::class.java))
            assertNotNull(ctx.tryGetBean("startZonedDateTime"))
        }
    }

    @Test
    fun testDestroyMethod() {
        var bean1: AnnotationDestroyBean
        var bean2: SpecifyDestroyBean
        AnnotationApplicationContext(ScanConfiguration::class.java, config).use { ctx ->
            // test @PreDestroy:
            bean1 = ctx.getUniqueBean(AnnotationDestroyBean::class.java)
            bean2 = ctx.getUniqueBean(SpecifyDestroyBean::class.java)
            assertEquals("Scan App", bean1.appTitle)
            assertEquals("Scan App", bean2.appTitle)
        }
        assertNull(bean1.appTitle)
        assertNull(bean2.appTitle)
    }

    @Test
    fun testConverter() {
        AnnotationApplicationContext(ScanConfiguration::class.java, config).use { ctx ->
            val bean = ctx.getUniqueBean(ValueConverterBean::class.java)
            assertNotNull(bean.injectedBoolean)
            assertTrue(bean.injectedBooleanPrimitive)
            assertTrue(bean.injectedBoolean!!)

            assertNotNull(bean.injectedByte)
            assertEquals(123.toByte(), bean.injectedByte)
            assertEquals(123.toByte(), bean.injectedBytePrimitive)

            assertNotNull(bean.injectedShort)
            assertEquals(12345.toShort(), bean.injectedShort)
            assertEquals(12345.toShort(), bean.injectedShortPrimitive)

            assertNotNull(bean.injectedInteger)
            assertEquals(1234567, bean.injectedInteger)
            assertEquals(1234567, bean.injectedIntPrimitive)

            assertNotNull(bean.injectedLong)
            assertEquals(123456789000L, bean.injectedLong)
            assertEquals(123456789000L, bean.injectedLongPrimitive)

            assertNotNull(bean.injectedFloat)
            assertEquals(12345.6789f, bean.injectedFloat!!, 0.0001f)
            assertEquals(12345.6789f, bean.injectedFloatPrimitive, 0.0001f)

            assertNotNull(bean.injectedDouble)
            assertEquals(123456789.87654321, bean.injectedDouble!!, 0.0000001)
            assertEquals(123456789.87654321, bean.injectedDoublePrimitive, 0.0000001)

            assertEquals(LocalDate.parse("2023-03-29"), bean.injectedLocalDate)
            assertEquals(LocalTime.parse("20:45:01"), bean.injectedLocalTime)
            assertEquals(LocalDateTime.parse("2023-03-29T20:45:01"), bean.injectedLocalDateTime)
            assertEquals(ZonedDateTime.parse("2023-03-29T20:45:01+08:00[Asia/Shanghai]"), bean.injectedZonedDateTime)
            assertEquals(Duration.parse("P2DT3H4M"), bean.injectedDuration)
            assertEquals(ZoneId.of("Asia/Shanghai"), bean.injectedZoneId)

            assertNotNull(bean.studentBean)
            assertTrue(bean.teacherBean is TeacherBean)
        }
    }

    @Test
    fun testNested() {
        AnnotationApplicationContext(ScanConfiguration::class.java, config).use { ctx ->
            ctx.getUniqueBean(OuterBean::class.java)
            ctx.getUniqueBean(OuterBean.NestedBean::class.java)
        }
    }

    @Test
    fun testPrimary() {
        AnnotationApplicationContext(ScanConfiguration::class.java, config).use { ctx ->
            val person = ctx.getUniqueBean(PersonBean::class.java)
            assertEquals<Class<*>>(TeacherBean::class.java, person.javaClass)
            val dog = ctx.getUniqueBean(DogBean::class.java)
            assertEquals("Husky", dog.type)
        }
    }

    @Test
    fun testProxy() {
        AnnotationApplicationContext(ScanConfiguration::class.java, config).use { ctx ->
            // test proxy:
            val proxy = ctx.getUniqueBean(OriginBean::class.java)
            assertSame<Class<*>>(SecondProxyBean::class.java, proxy.javaClass)
            assertEquals("Scan App", proxy.name)
            assertEquals("v1.0", proxy.version)
            // make sure proxy.field is not injected:
            val nameField = OriginBean::class.java.getDeclaredField("name")
            val versionField = OriginBean::class.java.getDeclaredField("version")
            nameField.isAccessible = true
            versionField.isAccessible = true
            assertNull(nameField.get(proxy))
            assertNull(versionField.get(proxy))

            // other beans are injected proxy instance:
            val inject1 = ctx.getUniqueBean(InjectProxyOnPropertyBean::class.java)
            val inject2 = ctx.getUniqueBean(InjectProxyOnConstructorBean::class.java)
            assertSame(proxy, inject1.injected)
            assertSame(proxy, inject2.injected)
        }
    }

    @Test
    fun testSub() {
        AnnotationApplicationContext(ScanConfiguration::class.java, config).use { ctx ->
            ctx.getUniqueBean(Sub1Bean::class.java)
            ctx.getUniqueBean(Sub2Bean::class.java)
            ctx.getUniqueBean(Sub3Bean::class.java)
        }
    }

    @Test
    fun testNotFound() {
        AnnotationApplicationContext(ScanConfiguration::class.java, config).use { ctx ->
            assertThrows<NoSuchBeanException> { ctx.getUniqueBean(Byte::class.java) }
            assertThrows<NoUniqueBeanException> { ctx.getUniqueBean(Any::class.java) }
            assertThrows<NoSuchBeanException> { ctx.getBean("dummy") }
            assertThrows<BeanTypeException> { ctx.getBean("startLocalDate", Int::class.java) }
        }
    }

    val config = ConfigProperties(
        mapOf(
            "app.title" to "Scan App",
            "app.version" to "v1.0",
            "jdbc.url" to "jdbc:hsqldb:file:testdb.tmp",
            "jdbc.username" to "",
            "jdbc.password" to "",
            "convert.boolean" to "true",
            "convert.byte" to "123",
            "convert.short" to "12345",
            "convert.integer" to "1234567",
            "convert.long" to "123456789000",
            "convert.float" to "12345.6789",
            "convert.double" to "123456789.87654321",
            "convert.localdate" to "2023-03-29",
            "convert.localtime" to "20:45:01",
            "convert.localdatetime" to "2023-03-29T20:45:01",
            "convert.zoneddatetime" to "2023-03-29T20:45:01+08:00[Asia/Shanghai]",
            "convert.duration" to "P2DT3H4M",
            "convert.zoneid" to "Asia/Shanghai",
        ).toProperties()
    )
}