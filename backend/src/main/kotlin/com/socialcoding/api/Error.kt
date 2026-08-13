package com.socialcoding.common

import kotlinx.serialization.Serializable

@Serializable open class APIError(val error: String) : Throwable(error)

class ServerError(override val message: String) : Throwable(message)

class NotFound(obj: String) : APIError("That $obj could not be found.")

class InvalidAuthorization() : Throwable("You do not have authorization for this.")

@Serializable
data class InvalidArguments(val arguments: Map<String, String>) :
    APIError("There was an issue with the arguments provided.")

fun invalidArguments(vararg arguments: Pair<String, String>): InvalidArguments =
    InvalidArguments(arguments.toMap())

fun arguments(collector: HashMap<String, String>.() -> Unit) {
    val map = hashMapOf<String, String>()
    collector.invoke(map)

    if (map.isNotEmpty()) throw InvalidArguments(map)
}

data class MissingArguments(val arguments: List<String>) :
    APIError("There are arguments missing from this request.")

fun missingArguments(vararg arguments: String): MissingArguments =
    MissingArguments(arguments.toList())

/** When receiving a request, the JSON is malformed. */
class MalformedBody : APIError("The body of this request is malformed.")

/**
 * A sign-in that couldn't be completed, carrying the reason. Unlike [InvalidAuthorization], the
 * message is written for the person signing in, so it goes back to them rather than becoming a
 * generic 500.
 */
class AuthorizationException(override val message: String) : APIError(message)
