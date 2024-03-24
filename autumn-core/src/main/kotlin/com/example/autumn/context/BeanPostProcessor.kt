package com.example.autumn.context

interface BeanPostProcessor {
    /**
     * Invoked after new Bean(), before @PostConstruct bean.init().
     */
    fun beforeInitialization(bean: Any, beanName: String): Any {
        return bean
    }

    /**
     * Invoked after @PostConstruct bean.init() called.
     */
    fun afterInitialization(bean: Any, beanName: String): Any {
        return bean
    }

    /**
     * Invoked before bean.setXyz() called.
     */
    fun beforePropertySet(bean: Any, beanName: String): Any {
        return bean
    }
}
