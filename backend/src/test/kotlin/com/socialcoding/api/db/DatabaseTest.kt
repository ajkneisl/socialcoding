package com.socialcoding.api.db

import com.socialcoding.Fixtures
import com.socialcoding.TestDatabase
import com.socialcoding.common.NotFound
import com.socialcoding.events.Event
import com.socialcoding.events.Events
import com.socialcoding.people.Role
import com.socialcoding.people.User
import com.socialcoding.people.Users
import com.socialcoding.projects.ProjectMembers
import com.socialcoding.projects.ProjectMembership
import com.socialcoding.projects.members.models.MemberStatus
import com.socialcoding.projects.tasks.ProjectTasks
import com.socialcoding.projects.tasks.toTask
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * The entity layer maps rows onto data classes by reflection, so a mismatch between a column and a
 * constructor parameter is a runtime failure rather than a compile error. These tests pin the
 * mapping rules the rest of the app relies on.
 */
class DatabaseTest {

    @BeforeTest fun clean() = TestDatabase.reset()

    // --- Column coercion --------------------------------------------------------------------

    @Test
    fun `string values are coerced to the column's type`() {
        val id = Uuid.random()
        assertEquals(id, Database.coerceToColumn(Users.id, id.toString()))
        assertEquals(42L, Database.coerceToColumn(Users.createdAt, "42"))
        assertEquals(2027, Database.coerceToColumn(Users.gradYear, "2027"))
        // A text column keeps the string it was handed.
        assertEquals("Ada", Database.coerceToColumn(Users.name, "Ada"))
    }

    @Test
    fun `non-string values pass through untouched`() {
        val id = Uuid.random()
        assertEquals(id, Database.coerceToColumn(Users.id, id))
        assertEquals(7L, Database.coerceToColumn(Users.createdAt, 7L))
        assertNull(Database.coerceToColumn(Users.gradYear, null))
    }

    // --- Table resolution -------------------------------------------------------------------

    @Test
    fun `an annotated class resolves to its table`() {
        assertEquals(Users, Database.resolveTable(User::class))
        assertEquals(Events, Database.resolveTable(Event::class))
    }

    @Test
    fun `a class without MappedTable is an error`() {
        // Uuid stands in for any class that was never mapped; the message names the offender.
        val failure = assertFailsWith<IllegalStateException> { Database.resolveTable(Uuid::class) }
        assertTrue(failure.message!!.contains("Uuid"))
    }

    // --- Row to entity ----------------------------------------------------------------------

    @Test
    fun `snake case columns map onto camel case parameters`() {
        val id =
            Fixtures.user(
                name = "Ada",
                role = Role.BOARD,
                avatarUrl = "https://example.test/ada.png",
                gradYear = 2027,
            )

        val user =
            transaction { Users.selectAll().where { Users.id eq id }.single().toEntity<User>() }

        // `avatar_url` and `grad_year` reach `avatarUrl` and `gradYear` with no explicit mapping.
        assertEquals("https://example.test/ada.png", user.avatarUrl)
        assertEquals(2027, user.gradYear)
        assertEquals(Role.BOARD, user.role)
        // A uuid column filling a String parameter is stringified rather than failing.
        assertEquals(id.toString(), user.id)
    }

    @Test
    fun `nullable columns arrive as null`() {
        val id = Fixtures.user()
        val user = transaction { Users.selectAll().where { Users.id eq id }.single().toEntity<User>() }

        assertNull(user.github)
        assertNull(user.linkedin)
        assertNull(user.title)
    }

    @Test
    fun `MapsTo overrides the derived column name`() {
        val owner = Fixtures.user()
        val project = Fixtures.project(owner)
        val first = Fixtures.task(project, "First")
        Fixtures.task(project, "Second", dependsOn = listOf(first))

        val second =
            transaction {
                ProjectTasks.selectAll().where { ProjectTasks.name eq "Second" }.single().toTask()
            }

        // `dependsOn` has no matching column; @MapsTo("depends_on_ids") points it at the right one.
        assertEquals(listOf(first), second.dependsOn)
    }

    @Test
    fun `collection parameters are decoded from their JSON column`() {
        val owner = Fixtures.user()
        val other = Fixtures.user()
        val project = Fixtures.project(owner)
        Fixtures.task(project, "Shared", assignees = listOf(owner, other))

        val task = transaction { ProjectTasks.selectAll().single().toTask() }

        assertEquals(listOf(owner.toString(), other.toString()), task.assigneeIds)
    }

    @Test
    fun `an optional parameter with no column falls back to its default`() {
        val author = Fixtures.user(name = "Author")
        Fixtures.event(author, title = "Kickoff")

        // `authorName` is joined in by the events layer, not stored on `events`, so the mapping
        // skips it and the constructor default stands.
        val event = transaction { Events.selectAll().single().toEntity<Event>(Events) }
        assertEquals("", event.authorName)
        assertEquals("Kickoff", event.title)
    }

    @Test
    fun `every mapped column is read, including a composite key`() {
        val owner = Fixtures.user()
        val invitee = Fixtures.user()
        val project = Fixtures.project(owner)
        Fixtures.invite(project, invitee, MemberStatus.PENDING)

        val membership = transaction { ProjectMembers.selectAll().single().toEntity<ProjectMembership>() }

        assertEquals(project.toString(), membership.projectID)
        assertEquals(invitee.toString(), membership.userID)
        assertEquals(MemberStatus.PENDING, membership.status)
    }

    // --- Lookups ----------------------------------------------------------------------------

    @Test
    fun `find returns the row as an entity`() = runBlocking {
        val id = Fixtures.user(name = "Grace")
        assertEquals("Grace", Users.find<User>(id).name)
    }

    @Test
    fun `find accepts a stringified key`() = runBlocking {
        val id = Fixtures.user(name = "Grace")
        // Route parameters arrive as strings; the key is coerced to the column's type.
        assertEquals("Grace", Users.find<User>(id.toString()).name)
    }

    @Test
    fun `find throws NotFound for a missing row`(): Unit = runBlocking {
        assertFailsWith<NotFound> { Users.find<User>(Uuid.random()) }
    }

    @Test
    fun `primaryKeyRow returns null for a missing row`() = runBlocking {
        assertNotNull(Users.primaryKeyRow(Fixtures.user()))
        assertNull(Users.primaryKeyRow(Uuid.random()))
    }

    @Test
    fun `exists answers for a key and for an entity`() = runBlocking {
        val id = Fixtures.user()
        assertTrue(Users.exists(id))
        assertFalse(Users.exists(Uuid.random()))

        val user = Users.find<User>(id)
        assertTrue(user.exists())
    }

    @Test
    fun `a by-key lookup needs a single-column primary key`(): Unit = runBlocking {
        // ProjectMembers is keyed on (project, user), so there's no single value to match.
        assertFailsWith<IllegalArgumentException> { ProjectMembers.exists(Uuid.random()) }
    }

    // --- Writes -----------------------------------------------------------------------------

    @Test
    fun `update writes a single property back to its column`() = runBlocking {
        val id = Fixtures.user(role = Role.MEMBER)
        val user = Users.find<User>(id)

        user.update(User::role, Role.BOARD).join()

        assertEquals(Role.BOARD, Users.find<User>(id).role)
        // The in-memory copy is untouched; the write goes to the row.
        assertEquals(Role.MEMBER, user.role)
    }

    @Test
    fun `update finds the row through a composite primary key`() = runBlocking {
        val owner = Fixtures.user()
        val invitee = Fixtures.user()
        val project = Fixtures.project(owner)
        Fixtures.invite(project, invitee, MemberStatus.PENDING)
        val membership = transaction { ProjectMembers.selectAll().single().toEntity<ProjectMembership>() }

        membership.update(ProjectMembership::status, MemberStatus.ACCEPTED).join()

        val status = transaction { ProjectMembers.selectAll().single()[ProjectMembers.status] }
        assertEquals(MemberStatus.ACCEPTED, status)
    }

    @Test
    fun `update leaves other rows alone`() = runBlocking {
        val first = Fixtures.user(name = "First")
        val second = Fixtures.user(name = "Second")

        Users.find<User>(first).update(User::title, "President").join()

        assertEquals("President", Users.find<User>(first).title)
        assertNull(Users.find<User>(second).title)
    }

    @Test
    fun `updating a property with no column fails the write, not the caller`() = runBlocking {
        val author = Fixtures.user(name = "Author")
        Fixtures.event(author, title = "Kickoff")
        val event = transaction { Events.selectAll().single().toEntity<Event>(Events) }

        // `authorName` is joined in rather than stored, so there's no column to write to. The
        // update runs on its own scope, whose handler absorbs the error — the caller gets a failed
        // job rather than an exception, and the row is left alone.
        val job = event.update(Event::authorName, "Someone Else")
        job.join()

        assertTrue(job.isCancelled, "the write failed rather than silently succeeding")
        assertEquals("Kickoff", transaction { Events.selectAll().single()[Events.title] })
    }
}
