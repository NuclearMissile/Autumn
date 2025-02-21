package io.nuclearmissile.autumn.eventbus

import io.nuclearmissile.autumn.annotation.Component
import io.nuclearmissile.autumn.annotation.Import
import io.nuclearmissile.autumn.annotation.Order
import io.nuclearmissile.autumn.annotation.Subscribe
import io.nuclearmissile.autumn.context.AnnotationApplicationContext
import io.nuclearmissile.autumn.utils.ConfigProperties
import org.junit.jupiter.api.BeforeEach
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


@Import(EventBusConfiguration::class)
class EventBusTestConfiguration

data class TestEventSync(val message: String) : Event
data class TestEventAsync(val message: String) : Event
data class TestEventUnregister(val message: String) : Event
data class TestEventOrder(val message: String) : Event

var testEventMessageSync = ""
var testEventMessageAsync = ""
var testEventMessageUnregister = ""
var testEventMessageOrder1 = ""
var testEventMessageOrder2 = ""

@Component
class TestEventSyncListener {
    @Subscribe(EventMode.SYNC)
    fun onTestEventSync(event: TestEventSync) {
        testEventMessageSync = event.message
    }
}

@Component
class TestEventAsyncListener {
    @Subscribe
    fun onTestEventAsync(event: TestEventAsync) {
        Thread.sleep(10)
        testEventMessageAsync = event.message
    }
}

@Component
class TestEventUnregisterListener {
    @Subscribe(EventMode.SYNC)
    fun onTestEventUnregister(event: TestEventUnregister) {
        testEventMessageUnregister = event.message
    }
}

@Component
class TestEventOrderListener {
    @Order(1)
    @Subscribe(EventMode.SYNC)
    fun onTestEventOrder1(event: TestEventOrder) {
        testEventMessageOrder1 += "1"
    }

    @Order(2)
    @Subscribe(EventMode.SYNC)
    fun onTestEventOrder2(event: TestEventOrder) {
        testEventMessageOrder1 += "2"
    }

    @Order(4)
    @Subscribe(EventMode.SYNC)
    fun onTestEventOrder3(event: TestEventOrder) {
        testEventMessageOrder2 += "3"
    }

    @Order(3)
    @Subscribe(EventMode.SYNC)
    fun onTestEventOrder4(event: TestEventOrder) {
        testEventMessageOrder2 += "4"
    }
}

class EventBusTest {
    @BeforeEach
    fun setUp() {
        testEventMessageSync = ""
        testEventMessageAsync = ""
        testEventMessageUnregister = ""
        testEventMessageOrder1 = ""
        testEventMessageOrder2 = ""
    }

    @Test
    fun testPostTestEventOrder() {
        AnnotationApplicationContext(EventBusTestConfiguration::class.java, ConfigProperties(emptyMap())).use {
            val eventBus = it.getUniqueBean(EventBus::class.java)
            eventBus.post(TestEventOrder("test"))
            assertEquals("12", testEventMessageOrder1)
            assertEquals("43", testEventMessageOrder2)
        }
    }

    @Test
    fun testPostTestEventSync() {
        AnnotationApplicationContext(EventBusTestConfiguration::class.java, ConfigProperties(emptyMap())).use {
            val eventBus = it.getUniqueBean(EventBus::class.java)
            assertEquals("", testEventMessageSync)
            eventBus.post(TestEventSync("test_sync"))
            assertEquals("test_sync", testEventMessageSync)
        }
    }

    @Test
    fun testPostTestEventAsync() {
        AnnotationApplicationContext(EventBusTestConfiguration::class.java, ConfigProperties(emptyMap())).use {
            val eventBus = it.getUniqueBean(EventBus::class.java)
            assertEquals("", testEventMessageAsync)
            eventBus.post(TestEventAsync("test_async"))
            assertEquals("", testEventMessageAsync)
            Thread.sleep(100)
            assertEquals("test_async", testEventMessageAsync)
        }
    }

    @Test
    fun testPostTestEventUnregister() {
        AnnotationApplicationContext(EventBusTestConfiguration::class.java, ConfigProperties(emptyMap())).use {
            val eventBus = it.getUniqueBean(EventBus::class.java)
            assertTrue(eventBus.isRegistered(it.getUniqueBean(TestEventUnregisterListener::class.java)))
            assertEquals("", testEventMessageUnregister)
            eventBus.post(TestEventUnregister("test_unregister"))
            assertEquals("test_unregister", testEventMessageUnregister)
            eventBus.unregister(it.getUniqueBean(TestEventUnregisterListener::class.java))
            assertFalse(eventBus.isRegistered(it.getUniqueBean(TestEventUnregisterListener::class.java)))
            eventBus.post(TestEventUnregister(""))
            assertEquals("test_unregister", testEventMessageUnregister)
        }
    }
}