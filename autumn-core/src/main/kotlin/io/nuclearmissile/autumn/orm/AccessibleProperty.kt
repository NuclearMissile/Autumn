package io.nuclearmissile.autumn.orm

interface PropertySetter {
    fun set(bean: Any, value: Any?)
}

interface PropertyGetter {
    fun get(bean: Any): Any?
}

class AccessibleProperty() {
}