package org.example.autumn.aop

import java.lang.reflect.Method

class InvocationChain(handlers: List<Invocation>) {
    private var result: Any? = null
    private var handlersIt = handlers.iterator()

    fun invokeChain(caller: Any, method: Method, args: Array<Any?>?): Any? {
        result = if (handlersIt.hasNext()) {
            handlersIt.next().invoke(caller, method, this, args)
        } else {
            method.invoke(caller, *(args ?: emptyArray()))
        }
        return result
    }
}

interface InvocationAdapter : Invocation {
    fun before(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?) {
        // do nothing
    }

    fun after(caller: Any, returnValue: Any?, method: Method, chain: InvocationChain, args: Array<Any?>?): Any? {
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