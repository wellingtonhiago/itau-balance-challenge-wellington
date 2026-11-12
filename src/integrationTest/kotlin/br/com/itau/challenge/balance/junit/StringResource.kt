package br.com.itau.challenge.balance.junit

import org.junit.jupiter.api.extension.ExtendWith

/** Injects the content of the classpath resource at [path] into a `String` test parameter. */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@ExtendWith(StringResourceParameterResolver::class)
annotation class StringResource(val path: String)