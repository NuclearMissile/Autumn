package com.example.autumn.jdbc.tx

import com.example.autumn.annotation.Transactional
import com.example.autumn.aop.AnnotationProxyBeanPostProcessor

class TransactionalBeanPostProcessor : AnnotationProxyBeanPostProcessor<Transactional>()
