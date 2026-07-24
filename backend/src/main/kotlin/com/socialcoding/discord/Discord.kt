package com.socialcoding.discord

import com.socialcoding.Environment
import com.socialcoding.Initializable
import com.socialcoding.Initialize
import com.socialcoding.projects.Projects
import dev.kord.common.entity.Snowflake
import dev.kord.rest.service.RestClient
import dev.kord.rest.service.createTextChannel
import dev.kord.rest.service.patchTextChannel
import kotlin.system.exitProcess
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/** Discord integration. */
@Initialize
object Discord : Initializable {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * The Discord credentials.
     *
     * @param token The Discord bot token.
     * @param guildID The Social Coding Discord ID.
     * @param projectsCategoryID The category of the `Projects` section.
     * @param archiveCategoryID The category of the `Archived` section.
     */
    @Serializable
    data class Credentials(
        val token: String,
        val guildID: Snowflake,
        val projectsCategoryID: Snowflake,
        val archiveCategoryID: Snowflake,
    )

    /** The credentials loaded from the `DISCORD` environment variable in [initialize]. */
    private lateinit var credentials: Credentials

    /** The RestClient. */
    private val rest by lazy { RestClient(credentials.token) }

    /**
     * Creates the project's message..
     *
     * @param projectID The ID of the project to create.
     */
    suspend fun onProjectApproved(projectID: Uuid) {
        val (title, description) =
            transaction {
                val row =
                    Projects.selectAll()
                        .where { Projects.id eq projectID }
                        .firstOrNull() ?: return@transaction null

                if (row[Projects.discordChannelId] != null)
                    return@transaction null

                row[Projects.title] to row[Projects.description]
            } ?: return

        try {
            val channel =
                rest.guild.createTextChannel(
                    credentials.guildID,
                    channelName(title),
                ) {
                    parentId = credentials.projectsCategoryID
                    topic = description
                }

            transaction {
                Projects.update({ Projects.id eq projectID }) {
                    it[discordChannelId] = channel.id.toString()
                }
            }

            rest.channel.createMessage(channel.id) {
                content =
                    """
                    Welcome to Social Coding, $title team!
                    
                    This channel can be your means of communication or feel free to take it elsewhere! If the board 
                    has any questions about your project, or there's any deadlines that are approaching, we will contact
                    you all here.
                    """
                        .trimIndent()
            }
        } catch (ex: Exception) {
            log.warn(
                "Failed to create Discord channel for project $projectID",
                ex,
            )
        }
    }

    /** When a project is retired, move them to the archive section. */
    suspend fun onProjectRetired(projectID: Uuid) =
        moveChannel(
            projectID,
            """
            This project has been marked as inactive. 
            If you think this is a mistake, please reach out to a board member.
            """
                .trimIndent(),
            credentials.archiveCategoryID,
        )

    /** When a project is activated, move them to the projects section. */
    suspend fun onProjectActivated(projectID: Uuid) =
        moveChannel(
            projectID,
            """
            This project has been marked active for this semester.
            """
                .trimIndent(),
            credentials.projectsCategoryID,
        )

    /** Re-parents the project's channel to [categoryID]. */
    private suspend fun moveChannel(
        projectID: Uuid,
        message: String,
        categoryID: Snowflake?,
    ) {
        val channelID =
            transaction {
                Projects.selectAll()
                    .where { Projects.id eq projectID }
                    .firstOrNull()
                    ?.get(Projects.discordChannelId)
                    ?.toULongOrNull()
                    ?.let { Snowflake(it) }
            } ?: return

        runCatching {
                rest.channel.patchTextChannel(channelID) {
                    parentId = categoryID
                }
                rest.channel.createMessage(channelID) { content = message }
            }
            .onFailure {
                log.warn(
                    "Failed to move Discord channel for project $projectID",
                    it,
                )
            }
    }

    /** Turns a project title into a valid Discord channel name. */
    private fun channelName(title: String): String =
        title
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(100)
            .ifBlank { "project" }

    override suspend fun initialize() {
        credentials =
            try {
                Json.decodeFromString(Environment.getVariable("DISCORD"))
            } catch (ex: Exception) {
                log.error("[FATAL] Discord was not properly set up.", ex)
                exitProcess(-1)
            }

        runCatching { rest.guild.getGuild(credentials.guildID) }
            .onFailure { ex ->
                log.error(
                    "[FATAL] Discord credentials are invalid or the bot can't reach guild ${credentials.guildID}.",
                    ex,
                )
                exitProcess(-1)
            }

        log.info("Discord initialized for guild {}", credentials.guildID)
    }
}
