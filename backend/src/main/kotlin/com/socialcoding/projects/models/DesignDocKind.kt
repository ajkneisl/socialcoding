package com.socialcoding.projects.models

/**
 * If the design doc is for an initial or returning project.
 *
 * @see DesignDocEntry
 */
enum class DesignDocKind {
    /** The initial proposal for the project. */
    INITIAL,

    /** A continuation of a project. */
    RETURNING,
}
