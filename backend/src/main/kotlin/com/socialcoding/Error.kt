package com.socialcoding.common

import kotlinx.serialization.Serializable

@Serializable data class ApiError(val error: String)

class ServerError(override val message: String) : Throwable(message)

class NotFound(obj: String) : Throwable("That $obj could not be found.")

class InvalidAuthorization() : Throwable("You do not have authorization for this.")

class AuthorizationException(override val message: String) : Throwable(message)
