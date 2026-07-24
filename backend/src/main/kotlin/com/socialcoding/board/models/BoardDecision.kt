package com.socialcoding.board.models

/** A finalized board decision on a project, taken from the `{decision}` path segment. */
enum class BoardDecision {
    APPROVE,
    REJECT,
    ACTIVATE,
    DEACTIVATE,
}
