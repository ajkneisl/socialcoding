package com.socialcoding.projects

import com.socialcoding.Fixtures
import com.socialcoding.TestDatabase
import com.socialcoding.projects.likes.models.LikeResult
import com.socialcoding.projects.models.ProjectStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ProjectLikesTest {

    @BeforeTest fun clean() = TestDatabase.reset()

    @Test
    fun `only approved projects count as likeable`() {
        val owner = Fixtures.user()
        assertTrue(approvedProjectExists(Fixtures.project(owner, ProjectStatus.APPROVED)))
        assertFalse(approvedProjectExists(Fixtures.project(owner, ProjectStatus.PENDING)))
        assertFalse(approvedProjectExists(Fixtures.project(owner, ProjectStatus.REJECTED)))
        assertFalse(approvedProjectExists(Uuid.random()))
    }

    @Test
    fun `a like toggles off on the second press`() {
        val project = Fixtures.project(Fixtures.user(), ProjectStatus.APPROVED)
        val fan = Fixtures.user()

        assertEquals(LikeResult(liked = true, likes = 1), toggleLike(project, fan))
        assertEquals(LikeResult(liked = false, likes = 0), toggleLike(project, fan))
        assertEquals(LikeResult(liked = true, likes = 1), toggleLike(project, fan))
    }

    @Test
    fun `each user's like counts once`() {
        val project = Fixtures.project(Fixtures.user(), ProjectStatus.APPROVED)
        val first = Fixtures.user()
        val second = Fixtures.user()

        toggleLike(project, first)
        assertEquals(LikeResult(liked = true, likes = 2), toggleLike(project, second))

        // One fan unhearting leaves the other's like in place.
        assertEquals(LikeResult(liked = false, likes = 1), toggleLike(project, first))
    }

    @Test
    fun `unliking one project doesn't touch another`() {
        val owner = Fixtures.user()
        val fan = Fixtures.user()
        val first = Fixtures.project(owner, ProjectStatus.APPROVED)
        val second = Fixtures.project(owner, ProjectStatus.APPROVED)

        toggleLike(first, fan)
        toggleLike(second, fan)
        toggleLike(first, fan)

        assertEquals(mapOf(second to 1L), transaction { likeCountsFor(listOf(first, second)) })
    }

    @Test
    fun `counts come back keyed by project, omitting the unliked`() {
        val owner = Fixtures.user()
        val liked = Fixtures.project(owner, ProjectStatus.APPROVED)
        val ignored = Fixtures.project(owner, ProjectStatus.APPROVED)
        Fixtures.like(liked, Fixtures.user())
        Fixtures.like(liked, Fixtures.user())

        val counts = transaction { likeCountsFor(listOf(liked, ignored)) }
        assertEquals(2L, counts[liked])
        assertEquals(null, counts[ignored], "a project with no hearts is simply absent")
    }

    @Test
    fun `asking about no projects hits no query`() {
        assertEquals(emptyMap(), transaction { likeCountsFor(emptyList()) })
        assertEquals(emptySet(), transaction { likedProjectIds(Uuid.random(), emptyList()) })
    }

    @Test
    fun `a user's likes are reported only for the projects asked about`() {
        val owner = Fixtures.user()
        val fan = Fixtures.user()
        val other = Fixtures.user()
        val mine = Fixtures.project(owner, ProjectStatus.APPROVED)
        val theirs = Fixtures.project(owner, ProjectStatus.APPROVED)
        val unasked = Fixtures.project(owner, ProjectStatus.APPROVED)
        Fixtures.like(mine, fan)
        Fixtures.like(unasked, fan)
        Fixtures.like(theirs, other)

        assertEquals(setOf(mine), transaction { likedProjectIds(fan, listOf(mine, theirs)) })
    }
}
