package org.example.autumn.eventbus

import org.example.autumn.annotation.*
import org.example.autumn.context.AnnotationConfigApplicationContext
import org.example.autumn.resolver.Config
import org.junit.jupiter.api.BeforeEach
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ComponentScan
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
        AnnotationConfigApplicationContext(EventBusTestConfiguration::class.java, Config(Properties())).use {
            val eventBus = it.getBean(EventBus::class.java)
            eventBus.post(TestEventOrder("test"))
            assertEquals("12", testEventMessageOrder1)
            assertEquals("43", testEventMessageOrder2)
        }
    }

    @Test
    fun testPostTestEventSync() {
        AnnotationConfigApplicationContext(EventBusTestConfiguration::class.java, Config(Properties())).use {
            val eventBus = it.getBean(EventBus::class.java)
            assertEquals("", testEventMessageSync)
            eventBus.post(TestEventSync("test_sync"))
            assertEquals("test_sync", testEventMessageSync)
        }
    }

    @Test
    fun testPostTestEventAsync() {
        AnnotationConfigApplicationContext(EventBusTestConfiguration::class.java, Config(Properties())).use {
            val eventBus = it.getBean(EventBus::class.java)
            assertEquals("", testEventMessageAsync)
            eventBus.post(TestEventAsync("test_async"))
            assertEquals("", testEventMessageAsync)
            Thread.sleep(100)
            assertEquals("test_async", testEventMessageAsync)
        }
    }

    @Test
    fun testPostTestEventUnregister() {
        AnnotationConfigApplicationContext(EventBusTestConfiguration::class.java, Config(Properties())).use {
            val eventBus = it.getBean(EventBus::class.java)
            assertTrue(eventBus.isRegistered(it.getBean(TestEventUnregisterListener::class.java)))
            assertEquals("", testEventMessageUnregister)
            eventBus.post(TestEventUnregister("test_unregister"))
            assertEquals("test_unregister", testEventMessageUnregister)
            eventBus.unregister(it.getBean(TestEventUnregisterListener::class.java))
            assertFalse(eventBus.isRegistered(it.getBean(TestEventUnregisterListener::class.java)))
            eventBus.post(TestEventUnregister(""))
            assertEquals("test_unregister", testEventMessageUnregister)
        }
    }
}