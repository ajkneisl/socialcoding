package com.socialcoding.projects.models

import kotlinx.serialization.Serializable

/** A [Project]'s answers to the Design Document. */
@Serializable
data class DesignDocContent(
    // About your Project
    val utilization: String = "",
    val services: String = "",
    val accessLocation: String = "",
    val intendedUsers: String = "",
    val goal: String = "",
    val usefulness: String = "",
    val demographic: String = "",
    val impact: String = "",
    val differentiation: String = "",
    val niceToHaves: String = "",

    // Architecture
    val serverNeeds: String = "",
    val databaseNeeds: String = "",
    val dataSchema: String = "",
    val dataProcurement: String = "",
    val dataProcessing: String = "",
    val softwareStack: String = "",

    // Teamwork
    val contributionExpectations: String = "",
    val communicationPlan: String = "",
)
