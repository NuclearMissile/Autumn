package com.example.scan

import com.example.autumn.annotation.Component

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Component
annotation class CustomAnnotation(val value: String = "")

@CustomAnnotation("customAnnotation")
class CustomAnnotationBean
