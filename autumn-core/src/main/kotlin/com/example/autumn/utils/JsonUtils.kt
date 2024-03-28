package com.example.autumn.utils

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer

object JsonUtils {
    /**
     * Holds ObjectMapper for internal use: NEVER modify!
     */
    val OBJECT_MAPPER = ObjectMapper().apply {
        setSerializationInclusion(JsonInclude.Include.ALWAYS)
        // add support for kotlin classes without no-arg ctor
        registerKotlinModule()
        // disabled features:
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    fun Any?.toJsonAsBytes(): ByteArray {
        return OBJECT_MAPPER.writeValueAsBytes(this)
    }

    fun Any?.toJson(): String {
        return OBJECT_MAPPER.writeValueAsString(this)
    }

    fun OutputStream.writeJson(obj: Any?): OutputStream {
        return this.also { OBJECT_MAPPER.writeValue(this, obj) }
    }

    fun Writer.writeJson(obj: Any?): Writer {
        return this.also { OBJECT_MAPPER.writeValue(it, obj) }
    }

    fun <T> String.readJson(clazz: Class<T>? = null, ref: TypeReference<T>? = null): T? {
        require(clazz != null || ref != null) { "must provide at least one clazz or ref parameter" }
        if (ref != null)
            return OBJECT_MAPPER.readValue(this, ref)
        return OBJECT_MAPPER.readValue(this, clazz)
    }

    inline fun <reified T> String.readJson(): T? {
        return OBJECT_MAPPER.readValue(this, T::class.java)
    }

    fun <T> Reader.readJson(clazz: Class<T>? = null, ref: TypeReference<T>? = null): T? {
        require(clazz != null || ref != null) { "must provide at least one clazz or ref parameter" }
        if (ref != null)
            return OBJECT_MAPPER.readValue(this, ref)
        return OBJECT_MAPPER.readValue(this, clazz)
    }

    inline fun <reified T> Reader.readJson(): T? {
        return OBJECT_MAPPER.readValue(this, T::class.java)
    }

    fun <T> InputStream.readJson(clazz: Class<T>? = null, ref: TypeReference<T>? = null): T? {
        require(clazz != null || ref != null) { "must provide at least one clazz or ref parameter" }
        if (ref != null)
            return OBJECT_MAPPER.readValue(this, ref)
        return OBJECT_MAPPER.readValue(this, clazz)
    }

    inline fun <reified T> InputStream.readJson(): T? {
        return OBJECT_MAPPER.readValue(this, T::class.java)
    }

    fun <T> ByteArray.readJson(clazz: Class<T>? = null, ref: TypeReference<T>? = null): T? {
        require(clazz != null || ref != null) { "must provide at least one clazz or ref parameter" }
        if (ref != null)
            return OBJECT_MAPPER.readValue(this, ref)
        return OBJECT_MAPPER.readValue(this, clazz)
    }

    inline fun <reified T> ByteArray.readJson(): T? {
        return OBJECT_MAPPER.readValue(this, T::class.java)
    }
}