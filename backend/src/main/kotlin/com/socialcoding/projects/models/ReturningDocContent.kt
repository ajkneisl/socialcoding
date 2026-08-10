package com.socialcoding.projects.models

import kotlinx.serialization.Serializable

/**
 * A returning [Project]'s answers to its semester design doc — what the team got done, what they're
 * doing next, and how the team has changed. Projects file one of these at the start of every
 * semester after the one they started in, where [DesignDocContent] is the proposal they started
 * with.
 */
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
