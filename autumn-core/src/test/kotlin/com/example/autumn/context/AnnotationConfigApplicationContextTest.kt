package com.example.autumn.context

import com.example.autumn.io.PropertyResolver
import com.example.imported.LocalDateConfiguration
import com.example.imported.ZonedDateConfiguration
import com.example.scan.*
import com.example.scan.InjectProxyOnConstructorBean
import com.example.scan.InjectProxyOnPropertyBean
import com.example.scan.OriginBean
import com.example.scan.SecondProxyBean
import com.example.scan.sub1.Sub1Bean
import com.example.scan.sub1.sub2.Sub2Bean
import com.example.scan.sub1.sub2.sub3.Sub3Bean
import org.junit.jupiter.api.Test
import java.time.*
import kotlin.test.*

class AnnotationConfigApplicationContextTest {
    @Test
    fun testCustomAnnotation() {
        AnnotationConfigApplicationContext(ScanApplication::class.java, createPropertyResolver()).use { ctx ->
            assertNotNull(ctx.getBean(CustomAnnotationBean::class.java))
            assertNotNull(ctx.getBean("customAnnotation"))
        }
    }

    @Test
    fun testInitMethod() {
        AnnotationConfigApplicationContext(ScanApplication::class.java, createPropertyResolver()).use { ctx ->
            // test @PostConstruct:
            val bean1 = ctx.getBean(AnnotationInitBean::class.java)
            val bean2 = ctx.getBean(SpecifyInitBean::class.java)
            assertEquals("Scan App / v1.0", bean1.appName)
            assertEquals("Scan App / v1.0", bean2.appName)
        }
    }

    @Test
    fun testImport() {
        AnnotationConfigApplicationContext(ScanApplication::class.java, createPropertyResolver()).use { ctx ->
            assertNotNull(ctx.getBean(LocalDateConfiguration::class.java))
            assertNotNull(ctx.getBean("startLocalDate"))
            assertNotNull(ctx.getBean("startLocalDateTime"))
            assertNotNull(ctx.getBean(ZonedDateConfiguration::class.java))
            assertNotNull(ctx.getBean("startZonedDateTime"))
        }
    }

    @Test
    fun testDestroyMethod() {
        var bean1: AnnotationDestroyBean
        var bean2: SpecifyDestroyBean
        AnnotationConfigApplicationContext(ScanApplication::class.java, createPropertyResolver()).use { ctx ->
            // test @PreDestroy:
            bean1 = ctx.getBean(AnnotationDestroyBean::class.java)
            bean2 = ctx.getBean(SpecifyDestroyBean::class.java)
            assertEquals("Scan App", bean1.appTitle)
            assertEquals("Scan App", bean2.appTitle)
        }
        assertNull(bean1.appTitle)
        assertNull(bean2.appTitle)
    }

    @Test
    fun testConverter() {
        AnnotationConfigApplicationContext(ScanApplication::class.java, createPropertyResolver()).use { ctx ->
            val bean = ctx.getBean(ValueConverterBean::class.java)
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
        }
    }

    @Test
    fun testNested() {
        AnnotationConfigApplicationContext(ScanApplication::class.java, createPropertyResolver()).use { ctx ->
            ctx.getBean(OuterBean::class.java)
            ctx.getBean(OuterBean.NestedBean::class.java)
        }
    }

    @Test
    fun testPrimary() {
        AnnotationConfigApplicationContext(ScanApplication::class.java, createPropertyResolver()).use { ctx ->
            val person = ctx.getBean(PersonBean::class.java)
            assertEquals<Class<*>>(TeacherBean::class.java, person.javaClass)
            val dog = ctx.getBean(DogBean::class.java)
            assertEquals("Husky", dog.type)
        }
    }

    @Test
    fun testProxy() {
        AnnotationConfigApplicationContext(ScanApplication::class.java, createPropertyResolver()).use { ctx ->
            // test proxy:
            val proxy = ctx.getBean(OriginBean::class.java)
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
            val inject1 = ctx.getBean(InjectProxyOnPropertyBean::class.java)
            val inject2 = ctx.getBean(InjectProxyOnConstructorBean::class.java)
            assertSame(proxy, inject1.injected)
            assertSame(proxy, inject2.injected)
        }
    }

    @Test
    fun testSub() {
        AnnotationConfigApplicationContext(ScanApplication::class.java, createPropertyResolver()).use { ctx ->
            ctx.getBean(Sub1Bean::class.java)
            ctx.getBean(Sub2Bean::class.java)
            ctx.getBean(Sub3Bean::class.java)
        }
    }


    private fun createPropertyResolver(): PropertyResolver {
        return PropertyResolver(
            mapOf(
                "app.title" to "Scan App",
                "app.version" to "v1.0",
                "jdbc.url" to "jdbc:hsqldb:file:testdb.tmp",
                "jdbc.username" to "sa",
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
}