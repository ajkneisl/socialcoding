package com.socialcoding

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

private fun RoutingContext.currentUserId(): Long =
    call.principal<JWTPrincipal>()!!.subject!!.toLong()

private fun RoutingContext.currentRole(): Role =
    Role.valueOf(call.principal<JWTPrincipal>()!!.payload.getClaim("role").asString())

private fun projectsWithOwners() =
    Projects.join(Users, JoinType.INNER, Projects.ownerId, Users.id).selectAll()

fun Route.apiRoutes(google: GoogleVerifier, sessions: SessionTokens, boardEmails: Set<String>) {
  route("/api") {
    get("/people") {
      val people = transaction {
        Users.selectAll().orderBy(Users.name).map { it.toPerson() }
      }
      call.respond(people)
    }

    get("/projects") {
      val projects = transaction {
        projectsWithOwners()
            .where { Projects.status eq ProjectStatus.APPROVED }
            .orderBy(Projects.submittedAt)
            .map { it.toProject(it[Users.name]) }
      }
      call.respond(projects)
    }

    post("/auth/google") {
      val identity = google.verify(call.receive<GoogleLoginRequest>().credential)

      val user = transaction {
        val existing =
            Users.selectAll()
                .where { (Users.googleSub eq identity.sub) or (Users.email eq identity.email) }
                .firstOrNull()
        // BOARD_EMAILS grants board access; seeded/claimed board members keep theirs.
        val role =
            when {
              identity.email.lowercase() in boardEmails -> Role.BOARD
              existing != null -> existing[Users.role]
              else -> Role.MEMBER
            }
        if (existing == null) {
          Users.insert {
            it[googleSub] = identity.sub
            it[email] = identity.email
            it[name] = identity.name
            it[avatarUrl] = identity.picture
            it[Users.role] = role
            it[createdAt] = System.currentTimeMillis()
          }
        } else {
          // Claims seeded rows and keeps profile/role in sync with Google + board list.
          Users.update({ Users.id eq existing[Users.id] }) {
            it[googleSub] = identity.sub
            it[avatarUrl] = identity.picture
            it[Users.role] = role
          }
        }
        Users.selectAll().where { Users.email eq identity.email }.first().toUser()
      }

      call.respond(LoginResponse(sessions.issue(user.id, user.role), user))
    }

    authenticate("session") {
      get("/me") {
        val userId = currentUserId()
        val user = transaction {
          Users.selectAll().where { Users.id eq userId }.firstOrNull()?.toUser()
        }
        if (user == null) call.respond(HttpStatusCode.NotFound, ApiError("User not found"))
        else call.respond(user)
      }

      post("/me") {
        val userId = currentUserId()
        val body = call.receive<UpdateProfileRequest>()
        val user = transaction {
          Users.update({ Users.id eq userId }) {
            it[joinedTerm] = body.joinedTerm
            it[gradYear] = body.gradYear
            it[github] = body.github
            it[linkedin] = body.linkedin
            it[website] = body.website
            it[company] = body.company
          }
          Users.selectAll().where { Users.id eq userId }.first().toUser()
        }
        call.respond(user)
      }

      post("/projects") {
        val userId = currentUserId()
        val body = call.receive<CreateProjectRequest>()
        if (body.title.isBlank() || body.description.isBlank()) {
          call.respond(HttpStatusCode.BadRequest, ApiError("Title and description are required"))
          return@post
        }
        val project = transaction {
          val id =
              Projects.insert {
                it[title] = body.title.trim()
                it[description] = body.description.trim()
                it[longDescription] = body.longDescription?.trim()?.ifBlank { null }
                it[repoUrl] = body.repoUrl?.trim()?.ifBlank { null }
                it[siteUrl] = body.siteUrl?.trim()?.ifBlank { null }
                it[ownerId] = userId
                it[status] = ProjectStatus.PENDING
                it[submittedAt] = System.currentTimeMillis()
              } get Projects.id
          projectsWithOwners().where { Projects.id eq id }.first().let {
            it.toProject(it[Users.name])
          }
        }
        call.respond(HttpStatusCode.Created, project)
      }

      get("/projects/mine") {
        val userId = currentUserId()
        val projects = transaction {
          projectsWithOwners()
              .where { Projects.ownerId eq userId }
              .orderBy(Projects.submittedAt)
              .map { it.toProject(it[Users.name]) }
        }
        call.respond(projects)
      }

      route("/board") {
        get("/projects") {
          if (currentRole() != Role.BOARD) {
            call.respond(HttpStatusCode.Forbidden, ApiError("Board access required"))
            return@get
          }
          val pending = transaction {
            projectsWithOwners()
                .where { Projects.status eq ProjectStatus.PENDING }
                .orderBy(Projects.submittedAt)
                .map { it.toProject(it[Users.name]) }
          }
          call.respond(pending)
        }

        post("/projects/{id}/{decision}") {
          if (currentRole() != Role.BOARD) {
            call.respond(HttpStatusCode.Forbidden, ApiError("Board access required"))
            return@post
          }
          val projectId = call.parameters["id"]?.toLongOrNull()
          val decision = call.parameters["decision"]
          if (projectId == null ||
              decision !in setOf("approve", "reject", "activate", "deactivate")) {
            call.respond(HttpStatusCode.BadRequest, ApiError("Unknown project or decision"))
            return@post
          }
          val note = runCatching { call.receive<ReviewRequest>().note }.getOrNull()
          val reviewerId = currentUserId()
          val updated = transaction {
            Projects.update({ Projects.id eq projectId }) {
              when (decision) {
                "approve" -> {
                  it[status] = ProjectStatus.APPROVED
                  it[reviewedBy] = reviewerId
                  it[reviewNote] = note
                }
                "reject" -> {
                  it[status] = ProjectStatus.REJECTED
                  it[reviewedBy] = reviewerId
                  it[reviewNote] = note
                }
                "activate" -> it[active] = true
                "deactivate" -> it[active] = false
              }
            }
          }
          if (updated == 0) call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
          else call.respond(HttpStatusCode.OK)
        }
      }
    }
  }
}
