package com.example.autumn.io

import java.io.InputStream

fun interface InputStreamCallback<T> {
    fun processInputStream(stream: InputStream): T
}
