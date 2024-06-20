package org.example.autumn.aop

import java.lang.reflect.Method

class InvocationChain(private val handlers: List<Invocation>) {
    private var result: Any? = null
    private var tasks = handlers.iterator()

    fun invokeChain(caller: Any, method: Method, args: Array<Any?>?): Any? {
        if (tasks.hasNext()) {
            val handler = tasks.next()
            result = handler.invoke(caller, method, this, args)
        } else {
            result = method.invoke(caller, *(args ?: emptyArray()))
            // reset the iterator otherwise the next method which should be proxied will not be handled
            tasks = handlers.iterator()
        }
        return result
    }
}

interface InvocationAdapter : Invocation {
    fun before(proxy: Any, method: Method, chain: InvocationChain, args: Array<Any?>?) {
        // do nothing
    }

    fun after(proxy: Any, returnValue: Any?, method: Method, chain: InvocationChain, args: Array<Any?>?): Any? {
        // do nothing
        return returnValue
    }

    override fun invoke(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?): Any? {
        before(caller, method, chain, args)
        val ret = chain.invokeChain(caller, method, args)
        return after(caller, ret, method, chain, args)
    }
}

interface Invocation {
    fun invoke(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?): Any?
}