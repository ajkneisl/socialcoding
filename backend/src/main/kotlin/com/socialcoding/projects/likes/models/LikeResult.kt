package com.socialcoding.projects.likes.models

import kotlinx.serialization.Serializable

/** The like count and the requesting user's like state for a project. */
@Serializable
data class LikeResult(val liked: Boolean, val likes: Long)