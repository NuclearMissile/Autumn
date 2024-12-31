package io.nuclearmissile.scan

import io.nuclearmissile.autumn.annotation.Component

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Component
annotation class CustomAnnotation(val value: String = "")

@CustomAnnotation("customAnnotation")
class CustomAnnotationBean
