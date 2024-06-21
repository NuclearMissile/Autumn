package org.example.autumn.aop

import java.lang.reflect.Method

class InvocationChain(handlers: List<Invocation>) {
    private val iter = handlers.iterator()

    fun invokeChain(caller: Any, method: Method, args: Array<Any?>?): Any? {
        return if (iter.hasNext()) {
            iter.next().invoke(caller, method, this, args)
        } else {
            method.invoke(caller, *(args ?: emptyArray()))
        }
    }
}

interface Invocation {
    fun before(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?) {
        // do nothing
    }

    fun after(caller: Any, returnValue: Any?, method: Method, chain: InvocationChain, args: Array<Any?>?): Any? {
        // do nothing
        return returnValue
    }

    fun invoke(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?): Any? {
        before(caller, method, chain, args)
        val ret = chain.invokeChain(caller, method, args)
        return after(caller, ret, method, chain, args)
    }
}
