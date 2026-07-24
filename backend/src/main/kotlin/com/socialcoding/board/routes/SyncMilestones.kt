package com.socialcoding.board.routes

import com.socialcoding.auth.currentRole
import com.socialcoding.board.BoardSettings
import com.socialcoding.common.InvalidAuthorization
import com.socialcoding.db.Role
import com.socialcoding.projects.syncMilestonesToAllProjects
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable

/**
 * The outcome of a milestone sync.
 *
 * @param projects How many projects had their milestones refreshed.
 */
@Serializable private data class SyncResult(val projects: Int)

/** POST /api/board/projects/sync-milestones — re-add MVP/Final milestones to every project. */
val SYNC_MILESTONES: suspend RoutingContext.() -> Unit = {
    if (currentRole() != Role.BOARD) throw InvalidAuthorization()

    val count = syncMilestonesToAllProjects(BoardSettings.presentationDates())
    call.respond(SyncResult(count))
}
