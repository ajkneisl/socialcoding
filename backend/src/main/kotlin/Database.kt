package com.socialcoding

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object Users : Table("users") {
  val id = long("id").autoIncrement()
  val googleSub = varchar("google_sub", 64).nullable().uniqueIndex()
  val email = varchar("email", 255).uniqueIndex()
  val name = varchar("name", 255)
  val avatarUrl = varchar("avatar_url", 512).nullable()
  val role = enumerationByName("role", 16, Role::class).default(Role.MEMBER)
  val joinedTerm = varchar("joined_term", 32).nullable()
  val gradYear = integer("grad_year").nullable()
  val github = varchar("github", 255).nullable()
  val linkedin = varchar("linkedin", 255).nullable()
  val website = varchar("website", 512).nullable()
  val company = varchar("company", 255).nullable()
  val title = varchar("title", 64).nullable()
  val createdAt = long("created_at")

  override val primaryKey = PrimaryKey(id)
}

object Projects : Table("projects") {
  val id = long("id").autoIncrement()
  val title = varchar("title", 200)
  val description = text("description")
  val longDescription = text("long_description").nullable()
  val active = bool("active").default(true)
  val repoUrl = varchar("repo_url", 512).nullable()
  val siteUrl = varchar("site_url", 512).nullable()
  val ownerId = long("owner_id").references(Users.id)
  val status = enumerationByName("status", 16, ProjectStatus::class).default(ProjectStatus.PENDING)
  val submittedAt = long("submitted_at")
  val reviewedBy = long("reviewed_by").references(Users.id).nullable()
  val reviewNote = varchar("review_note", 1000).nullable()

  override val primaryKey = PrimaryKey(id)
}

fun ResultRow.toPerson() =
    PersonDto(
        id = this[Users.id],
        name = this[Users.name],
        joinedTerm = this[Users.joinedTerm],
        gradYear = this[Users.gradYear],
        github = this[Users.github],
        linkedin = this[Users.linkedin],
        website = this[Users.website],
        company = this[Users.company],
        role = this[Users.role],
        title = this[Users.title],
        avatarUrl = this[Users.avatarUrl],
    )

fun ResultRow.toUser() =
    UserDto(
        id = this[Users.id],
        email = this[Users.email],
        name = this[Users.name],
        role = this[Users.role],
        joinedTerm = this[Users.joinedTerm],
        gradYear = this[Users.gradYear],
        github = this[Users.github],
        linkedin = this[Users.linkedin],
        website = this[Users.website],
        company = this[Users.company],
        title = this[Users.title],
        avatarUrl = this[Users.avatarUrl],
    )

fun ResultRow.toProject(ownerName: String) =
    ProjectDto(
        id = this[Projects.id],
        title = this[Projects.title],
        description = this[Projects.description],
        longDescription = this[Projects.longDescription],
        active = this[Projects.active],
        repoUrl = this[Projects.repoUrl],
        siteUrl = this[Projects.siteUrl],
        status = this[Projects.status],
        ownerName = ownerName,
        submittedAt = this[Projects.submittedAt],
        reviewNote = this[Projects.reviewNote],
    )

object DatabaseFactory {

  fun init(jdbcUrl: String, driver: String, user: String, password: String) {
    Database.connect(jdbcUrl, driver = driver, user = user, password = password)
    transaction {
      SchemaUtils.create(Users, Projects)
      if (Users.selectAll().empty()) seed()
    }
  }

  /** Seed data mirroring the membership roster and active projects on socialcoding.net. */
  private fun seed() {
    val now = System.currentTimeMillis()

    // Companies are placeholders until members claim their accounts and fill in their own.
    data class SeedMember(
        val name: String,
        val term: String? = null,
        val year: Int? = null,
        val company: String? = null,
        val boardTitle: String? = null,
        val githubUser: String? = null,
    )

    val members =
        listOf(
            SeedMember("Vitaly Kornev", company = "IBM", boardTitle = "President"),
            SeedMember("Tony Rutherford", company = "Belvedere", boardTitle = "Vice President"),
            SeedMember("AJ Kneisl", company = "RBC", boardTitle = "Treasurer", githubUser = "ajkneisl"),
            SeedMember("Yord Eshete", company = "OIT", boardTitle = "Vice Treasurer"),
            SeedMember("AJ Lange", "Fall 2023", 2027),
            SeedMember("Adam Douiri", "Fall 2023", 2026, "Capital One", "Events"),
            SeedMember("Adhithya Anandaraj", "Fall 2023", 2025, "Medtronic"),
            SeedMember("Aidan Ruiz", "Fall 2023", 2025),
            SeedMember("Aitan Singh", "Fall 2023", 2026, "U.S. Bank"),
            SeedMember("Andrew O'Konski-Magnuson", "Spring 2023", 2025),
            SeedMember("Andy Phu", "Fall 2023", 2025, "Optum"),
            SeedMember("Anne Kolstad", "Fall 2022", 2024, "Microsoft"),
            SeedMember("Ansh Patel", "Fall 2022", 2025, "General Mills"),
            SeedMember("Bernie Nnadi", "Spring 2024", 2026),
            SeedMember("Bethany Freeman", "Fall 2023", 2024, "Epic"),
            SeedMember("Blake Hokanson", "Fall 2022", 2025, "Boston Scientific"),
            SeedMember("Zane Hungerford", boardTitle = "Recruitment"),
            SeedMember("Emma Nguyen", boardTitle = "Communications"),
            SeedMember("Rahil Sheth", boardTitle = "Relations"),
        )

    val memberIds =
        members.map { member ->
          Users.insert {
            it[email] = member.name.lowercase().replace(Regex("[^a-z]+"), ".") + "@umn.edu"
            it[name] = member.name
            it[joinedTerm] = member.term
            it[gradYear] = member.year
            it[company] = member.company
            it[github] = member.githubUser
            it[role] = if (member.boardTitle != null) Role.BOARD else Role.MEMBER
            it[title] = member.boardTitle
            it[createdAt] = now
          } get Users.id
        }

    data class SeedProject(
        val title: String,
        val desc: String,
        val longDesc: String? = null,
        val repo: String? = null,
        val site: String? = null,
        val isActive: Boolean = true,
        val owner: String? = null,
    )

    val seedProjects =
        listOf(
            SeedProject(
                "Burrow",
                "A study group finder for the University of Minnesota — find your people at umn.app.",
                "Burrow helps UMN students find and form study groups for their classes. " +
                    "Kotlin/Ktor on the backend, a TypeScript/React web app, and a React Native " +
                    "mobile app — a full-stack project with room for contributors at every layer.",
                "https://github.com/ajkneisl/burrow",
                "https://umn.app",
                owner = "AJ Kneisl",
            ),
            SeedProject(
                "MP3 Metadata",
                "A web-based editor for inspecting and fixing the ID3 tags on your music library, right in the browser.",
                "Drag in any MP3 and edit its title, artist, album art, and the rest of its ID3 tags " +
                    "without installing anything — parsing and rewriting happen entirely client-side, " +
                    "so files never leave your machine. A good first project if you want to learn " +
                    "binary file formats and modern browser APIs.",
                "https://github.com/social-coding-umn/mp3-metadata",
            ),
            SeedProject(
                "Interactive Prerequisite Flowchart",
                "Search any UMN course and explore an interactive flowchart of everything it unlocks and everything it requires.",
                "Course catalogs tell you a class's prerequisites, but not what taking it unlocks. " +
                    "This tool scrapes the UMN catalog into a dependency graph and renders it as an " +
                    "explorable flowchart, so you can plan multi-semester paths through a major at a glance.",
                "https://github.com/social-coding-umn/prereq-flowchart",
            ),
            SeedProject(
                "GopherMatch",
                "A roommate-matching platform built for UMN students to find compatible living situations.",
                "A profile-and-preferences matching site for students hunting for roommates: sleep " +
                    "schedules, study habits, budgets, and neighborhoods all factor into match scores. " +
                    "Full-stack work across a React frontend, REST API, and recommendation logic.",
                "https://github.com/social-coding-umn/gophermatch",
            ),
            SeedProject(
                "Gopher Transit x Metro Busses",
                "Live tracking for campus shuttles and Metro Transit buses in one unified map.",
                "Campus shuttles and Metro Transit expose separate real-time feeds; this project " +
                    "merges them into one live map with arrival predictions for stops around campus, " +
                    "so you can decide between the 121 and the Green Line without opening two apps.",
                "https://github.com/social-coding-umn/gopher-transit",
            ),
            SeedProject(
                "Computer Vision Wheelchair",
                "An accessibility project exploring computer-vision-assisted navigation for powered wheelchairs.",
                "Working with depth cameras and on-device inference to help powered-wheelchair users " +
                    "detect obstacles, doorways, and curb cuts. Hardware meets software: ROS, sensor " +
                    "fusion, and a lot of real-world testing around campus.",
                "https://github.com/social-coding-umn/cv-wheelchair",
            ),
            SeedProject(
                "Course Review Aggregator",
                "Pulled grade distributions and student reviews into one searchable view of every UMN class.",
                isActive = false,
            ),
            SeedProject(
                "Club Website v1",
                "The original socialcoding.net — retired when the site you're reading replaced it.",
                repo = "https://github.com/social-coding-umn/website",
                isActive = false,
            ),
        )

    val idsByName = members.map { it.name }.zip(memberIds).toMap()

    seedProjects.forEachIndexed { i, project ->
      Projects.insert {
        it[title] = project.title
        it[description] = project.desc
        it[longDescription] = project.longDesc
        it[repoUrl] = project.repo
        it[siteUrl] = project.site
        it[active] = project.isActive
        it[ownerId] = idsByName[project.owner] ?: memberIds[i % memberIds.size]
        it[status] = ProjectStatus.APPROVED
        it[submittedAt] = now
      }
    }
  }
}
