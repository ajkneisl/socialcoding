package com.socialcoding.projects.models

/** Which set of questions a semester's design doc asks. */
enum class DesignDocKind {
    /** The project proposal, filed the semester the project starts. */
    INITIAL,

    /** The check-in a returning project files at the start of every semester after that. */
    RETURNING,
}
