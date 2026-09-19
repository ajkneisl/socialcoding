package com.socialcoding.projects.models

import kotlinx.serialization.Serializable

/** A returning [Project]'s answers to its semester design doc. */
@Serializable
data class ReturningDocContent(
    // Last semester
    val accomplishments: String = "",
    val unfinished: String = "",
    val challenges: String = "",

    // This semester
    val goals: String = "",
    val scopeChanges: String = "",
    val architectureChanges: String = "",

    // Teamwork
    val teamChanges: String = "",
    val contributionExpectations: String = "",
    val communicationPlan: String = "",
)
