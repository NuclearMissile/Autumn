package org.example.autumn.aop

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

interface InvocationHandlerAdapter : InvocationHandler {
    fun before(proxy: Any, method: Method, args: Array<Any?>?) {
        // do nothing
    }

    fun after(proxy: Any, returnValue: Any, method: Method, args: Array<Any?>?): Any {
        // do nothing
        return returnValue
    }

    override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
        // 拦截标记了@Polite的方法返回值:
        before(proxy, method, args)
        val ret = method.invoke(proxy, *(args ?: emptyArray()))
        return after(proxy, ret, method, args)
    }
}