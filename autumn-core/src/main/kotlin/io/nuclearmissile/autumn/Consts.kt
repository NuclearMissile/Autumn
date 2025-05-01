package io.nuclearmissile.autumn

import io.nuclearmissile.autumn.aop.AroundConfiguration
import io.nuclearmissile.autumn.context.ApplicationContextConfiguration
import io.nuclearmissile.autumn.db.DbConfiguration
import io.nuclearmissile.autumn.eventbus.EventBusConfiguration
import io.nuclearmissile.autumn.servlet.WebMvcConfiguration
import java.util.*

const val DUMMY_VALUE = "\n\t\t\n\t\t\n\ue000\ue001\ue002\n\t\t\t\t\n"

const val DEFAULT_ORDER = Int.MAX_VALUE - 10000

const val DEFAULT_TX_MANAGER_ORDER = Int.MAX_VALUE - 1000

const val CONFIG_YML = "/config.yml"

const val DEFAULT_CONFIG_YML = "/__default-config__.yml"

val DEFAULT_LOCALE: Locale = Locale.getDefault()

val IS_WINDOWS: Boolean = System.getProperty("os.name").lowercase().startsWith("windows")

val DEFAULT_ERROR_RESP_BODY = mapOf(
    400 to "<h1>400: Bad Request</h1>",
    401 to "<h1>401: Unauthorized</h1>",
    403 to "<h1>403: Forbidden</h1>",
    404 to "<h1>404: Not Found</h1>",
    429 to "<h1>429: Too many Requests</h1>",
    500 to "<h1>500: Internal Server Error</h1>",
    501 to "<h1>501: Not Implemented</h1>",
    502 to "<h1>502: Bad Gateway</h1>",
    503 to "<h1>503: Service Unavailable</h1>",
    504 to "<h1>504: Gateway Timeout</h1>",
)

val DEFAULT_CONFIGURATIONS = listOf(
    WebMvcConfiguration::class,
    DbConfiguration::class,
    AroundConfiguration::class,
    EventBusConfiguration::class,
    ApplicationContextConfiguration::class,
)