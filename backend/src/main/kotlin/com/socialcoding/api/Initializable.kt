package com.socialcoding.api

import kotlin.reflect.full.createInstance
import org.reflections.Reflections
import org.slf4j.LoggerFactory

/** A class that requires it to be initialized. */
interface Initializable {
    suspend fun initialize()
}

/**
 * Mark a class as [Initializable].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Initialize
{
    companion object
    {
        private val LOGGER = LoggerFactory.getLogger("Initializers")

        /**
         * Find all and execute all [Initialize] classes.
         */
        suspend fun runInitializers() {
            Reflections("com.socialcoding")
                .getSubTypesOf(Initializable::class.java)
                .filter { it.isAnnotationPresent(Initialize::class.java) }
                .forEach { clazz ->
                    LOGGER.debug("Initializing {}", clazz.simpleName)
                    val instance =
                        clazz.kotlin.objectInstance ?: clazz.kotlin.createInstance()

                    instance.initialize()
                }
        }
    }
}
