package com.example.autumn.resolver

import java.io.InputStream

fun interface InputStreamCallback<T> {
    fun processInputStream(stream: InputStream): T
}
