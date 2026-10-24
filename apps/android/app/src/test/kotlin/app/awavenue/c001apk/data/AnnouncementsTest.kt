package app.awavenue.c001apk.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementsTest {
    private val digest = "a".repeat(64)

    private fun item(
        id: String = "2026-10-23-test",
        title: String = "公告标题",
        summary: String = "摘要",
        publishedAt: String = "2026-10-23T15:30:20Z",
        contentRevision: Int = 1,
        reminderRevision: Int = 1,
        important: Boolean = false,
        path: String = "$id-1.md",
        withdrawn: Boolean = false,
        expiresAt: String? = null,
    ) = buildString {
        append("""{"id":"$id","title":"$title","summary":"$summary","publishedAt":"$publishedAt",""")
        append(""""contentRevision":$contentRevision,"reminderRevision":$reminderRevision,""")
        append(""""important":$important,"path":"$path","sha256":"$digest","withdrawn":$withdrawn""")
        expiresAt?.let { append(""","expiresAt":"$it"""") }
        append("}")
    }

    private fun index(vararg items: String, revision: Int = 1) =
        """{"schemaVersion":1,"revision":$revision,"announcements":[${items.joinToString(",")}]}"""

    @Test
    fun indexParsesAndSortsNewestFirst() {
        val parsed = AnnouncementJson.parseIndex(
            index(
                item(id = "older", publishedAt = "2026-10-20T08:00:00Z"),
                item(id = "newer", publishedAt = "2026-10-23T15:30:20+08:00"),
                revision = 2,
            ),
        )
        assertEquals(listOf("newer", "older"), parsed.entries.map { it.id })
        assertEquals(2, parsed.revision)
        assertEquals(0, parsed.entries[0].publishedAt % 1000)
    }

    @Test
    fun duplicateIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AnnouncementJson.parseIndex(index(item(id = "same"), item(id = "same")))
        }
    }

    @Test
    fun datesWithoutTimezoneAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AnnouncementJson.parseIndex(index(item(publishedAt = "2026-10-23T15:30:20")))
        }
    }

    @Test
    fun malformedPathsAndDigestsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AnnouncementJson.parseIndex(index(item(path = "../secret.md")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnnouncementJson.parseIndex(
                """{"schemaVersion":1,"revision":1,"announcements":[
                    {"id":"a","title":"t","summary":"","publishedAt":"2026-10-23T15:30:20Z",
                     "contentRevision":1,"reminderRevision":1,"important":false,
                     "path":"a.md","sha256":"xyz","withdrawn":false}]}""",
            )
        }
    }

    @Test
    fun revisionsAndFlagsMustBeWellFormed() {
        assertThrows(IllegalArgumentException::class.java) {
            AnnouncementJson.parseIndex(index(item(contentRevision = 0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnnouncementJson.parseIndex(index(item(reminderRevision = 0)))
        }
        val parsed = AnnouncementJson.parseIndex(index(item(important = true)))
        assertTrue(parsed.entries.single().important)
    }

    @Test
    fun fingerprintChangesOnlyWithContent() {
        val first = AnnouncementJson.parseIndex(index(item(), revision = 3))
        val same = AnnouncementJson.parseIndex(index(item(), revision = 3))
        val changed = AnnouncementJson.parseIndex(index(item(summary = "新摘要"), revision = 3))
        assertEquals(first.fingerprint(), same.fingerprint())
        assertTrue(first.fingerprint() != changed.fingerprint())
    }

    @Test
    fun withdrawnAndExpiredItemsAreInactive() {
        val now = 1_700_000_000_000L
        val parsed = AnnouncementJson.parseIndex(
            index(
                item(id = "live", expiresAt = "2030-01-01T00:00:00Z"),
                item(id = "expired", expiresAt = "2020-01-01T00:00:00Z"),
                item(id = "withdrawn", withdrawn = true),
            ),
        )
        val byId = parsed.entries.associateBy { it.id }
        assertTrue(byId.getValue("live").activeAt(now))
        assertFalse(byId.getValue("expired").activeAt(now))
        assertFalse(byId.getValue("withdrawn").activeAt(now))
    }

    @Test
    fun stateRoundTripKeepsTheTable() {
        val parsed = AnnouncementJson.parseIndex(index(item(id = "a"), item(id = "b"), revision = 4))
        val restored = AnnouncementJson.parseIndex(parsed.toJson().toString())
        assertEquals(parsed.revision, restored.revision)
        assertEquals(parsed.fingerprint(), restored.fingerprint())
    }
}
