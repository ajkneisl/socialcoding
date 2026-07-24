package com.socialcoding

import kotlin.reflect.full.createInstance
import org.reflections.Reflections
import org.slf4j.LoggerFactory

interface Initializable {
    suspend fun initialize()
}

@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.RUNTIME) annotation class Initialize

private val LOGGER = LoggerFactory.getLogger("Initializers")

suspend fun runInitializers() {
    Reflections("com.socialcoding")
        .getSubTypesOf(Initializable::class.java)
        .filter { it.isAnnotationPresent(Initialize::class.java) }
        .forEach { clazz ->
            LOGGER.debug("Initializing {}", clazz.simpleName)
            val instance = clazz.kotlin.objectInstance ?: clazz.kotlin.createInstance()

            instance.initialize()
        }
}
