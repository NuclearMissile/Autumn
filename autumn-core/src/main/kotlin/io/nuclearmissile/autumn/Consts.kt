package io.nuclearmissile.autumn

import java.util.*

const val DUMMY_VALUE = "\n\t\t\n\t\t\n\ue000\ue001\ue002\n\t\t\t\t\n"

const val DEFAULT_ORDER = Int.MAX_VALUE - 10000

const val DEFAULT_TX_MANAGER_ORDER = Int.MAX_VALUE - 1000

val DEFAULT_LOCALE: Locale = Locale.getDefault()

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