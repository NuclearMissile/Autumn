package com.example.autumn.aop

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

abstract class AfterInvocationHandlerAdapter : InvocationHandler {
    abstract fun after(proxy: Any, returnValue: Any, method: Method, args: Array<Any>?): Any

    override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any? {
        val ret = method.invoke(proxy, *(args ?: emptyArray()))
        return after(proxy, ret, method, args)
    }
}

abstract class BeforeInvocationHandlerAdapter : InvocationHandler {
    abstract fun before(proxy: Any, method: Method, args: Array<Any>?)

    override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any? {
        before(proxy, method, args)
        return method.invoke(proxy, *(args ?: emptyArray()))
    }
}