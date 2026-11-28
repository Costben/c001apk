package app.awavenue.c001apk.ui.feed

import app.awavenue.c001apk.model.Entity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedVoteTest {
    private fun entity(json: String) = Entity(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun detectsVoteFeedsFromFeedTypeOrNestedVote() {
        assertTrue(entity("""{"id":"1","feedType":"vote"}""").isVoteFeed)
        assertTrue(entity("""{"id":"2","feedType":"feed","vote":{"id":"10"}}""").isVoteFeed)
        assertFalse(entity("""{"id":"3","feedType":"feed"}""").isVoteFeed)
    }

    @Test
    fun ordersDeduplicatesAndFiltersVoteOptions() {
        val vote = entity(
            """
            {
              "id": "10",
              "options": [
                {"id": "2", "title": "Option B", "order": 2},
                {"id": "1", "title": "Option A", "order": 1},
                {"id": "2", "title": "Duplicate B", "order": 0},
                {"id": "", "title": "Missing Id", "order": 3},
                {"id": "4", "title": "", "order": 4}
              ]
            }
            """.trimIndent(),
        )

        val options = vote.voteOptions()
        assertEquals(listOf("1", "2"), options.map { it.id })
        assertEquals(listOf("Option A", "Option B"), options.map { it.title })
    }

    @Test
    fun parsesSelectionRangeAndSelectedIds() {
        val vote = entity(
            """
            {
              "id": "11",
              "min_select_num": 0,
              "max_select_num": 3,
              "user_selected_option": " 101, 103 , "
            }
            """.trimIndent(),
        )

        assertEquals(1..3, vote.voteSelectionRange())
        assertEquals(setOf("101", "103"), vote.voteSelectedIds())

        val inverted = entity("""{"id":"12","min_select_num":3,"max_select_num":1}""")
        assertEquals(3..3, inverted.voteSelectionRange())
    }

    @Test
    fun checksUpcomingAndClosedBoundaries() {
        val vote = entity("""{"id":"13","start_time":1000,"end_time":2000}""")

        assertTrue(vote.isVoteUpcoming(999))
        assertFalse(vote.isVoteUpcoming(1000))
        assertFalse(vote.isVoteClosed(1999))
        assertTrue(vote.isVoteClosed(2000))
    }

    @Test
    fun remembersSubmittedVoteOverrideForFeed() {
        val initialVote = entity("""{"id":"20","user_selected_option":""}""")
        val submittedVote = entity("""{"id":"20","user_selected_option":"1"}""")
        val feed = Entity(
            Json.parseToJsonElement("""{"id":"999001","feedType":"vote","vote":${initialVote.json}}""").jsonObject,
        )

        assertEquals(initialVote, VoteActions.voteFor(feed))
        VoteActions.rememberVote(feed.id, submittedVote)
        assertSame(submittedVote, VoteActions.voteFor(feed))
    }
}
