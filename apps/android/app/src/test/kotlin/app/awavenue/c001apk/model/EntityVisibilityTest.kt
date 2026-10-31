package app.awavenue.c001apk.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityVisibilityTest {
    private fun entity(json: String) = Entity(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun readsBothPublishStatusSpellings() {
        assertTrue(entity("""{"id":"1","publishStatus":1}""").isSelfOnly)
        assertTrue(entity("""{"id":"1","publish_status":"1"}""").isSelfOnly)
    }

    @Test
    fun publicAndMissingScopesAreNotSelfOnly() {
        assertFalse(entity("""{"id":"1","publishStatus":0}""").isSelfOnly)
        // A feed without the field is public; the review status must not stand in for it.
        assertFalse(entity("""{"id":"1","status":"-1"}""").isSelfOnly)
    }
}
