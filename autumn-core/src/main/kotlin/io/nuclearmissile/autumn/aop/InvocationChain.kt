package io.nuclearmissile.autumn.aop

import io.nuclearmissile.autumn.utils.ClassUtils.extractTarget
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class InvocationChain(handlers: List<Invocation>) {
    private val iter = handlers.iterator()

    fun invokeChain(caller: Any, method: Method, args: Array<Any?>?): Any? {
        return try {
            if (iter.hasNext())
                iter.next().invoke(caller, method, this, args)
            else
                method.invoke(caller, *(args ?: emptyArray()))
        } catch (e: InvocationTargetException) {
            throw e.extractTarget()
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

    fun error(caller: Any, method: Method, chain: InvocationChain, e: Throwable, args: Array<Any?>?) {
        // do nothing but throw
        throw e
    }

    fun finally(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?) {
        // do nothing
    }

    fun invoke(caller: Any, method: Method, chain: InvocationChain, args: Array<Any?>?): Any? {
        return try {
            before(caller, method, chain, args)
            val returnValue = chain.invokeChain(caller, method, args)
            after(caller, returnValue, method, chain, args)
        } catch (e: Throwable) {
            error(caller, method, chain, e, args)
        } finally {
            finally(caller, method, chain, args)
        }
    }
}
