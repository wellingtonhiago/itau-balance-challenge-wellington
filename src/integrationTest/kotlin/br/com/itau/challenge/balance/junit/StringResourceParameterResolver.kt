package br.com.itau.challenge.balance.junit

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

class StringResourceParameterResolver : ParameterResolver {

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
        parameterContext.isAnnotated(StringResource::class.java) && parameterContext.parameter.type == String::class.java

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any =
        readResource(parameterContext.findAnnotation(StringResource::class.java).orElseThrow().path)
}

fun readResource(path: String): String =
    requireNotNull(StringResourceParameterResolver::class.java.classLoader.getResource(path)) {
        "Resource '$path' not found in the classpath"
    }.readText()