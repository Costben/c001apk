package app.awavenue.c001apk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TwilightRulesTest {
    private fun ruleSet(vararg rules: TwilightRule) = TwilightRuleSet(
        schemaVersion = 1,
        revision = 7,
        publishedAt = "2026-01-02T03:04:05Z",
        rules = rules.toList(),
    )

    private fun textRule(
        id: String = "text.spam",
        level: String = "standard",
        groups: List<List<String>> = listOf(listOf("代刷"), listOf("好评")),
        distance: Int = 10,
        exceptions: List<String> = emptyList(),
        scopes: List<String> = listOf("feed", "comment", "user"),
    ) = TwilightRule(
        id = id,
        reason = "测试规则",
        kind = "text",
        level = level,
        scopes = scopes,
        groups = groups,
        maxDistance = distance,
        exceptions = exceptions,
    )

    private fun engine(
        set: TwilightRuleSet,
        strict: Boolean = false,
        disabled: Set<String> = emptySet(),
    ) = ContentFilterEngine(
        ContentFilterPreferences(
            enabled = true,
            twilight = set,
            twilightStrict = strict,
            disabledTwilightRules = disabled,
        ),
    )

    private fun input(
        text: String,
        scope: FilterScope = FilterScope.Feed,
        author: String = "",
        topics: List<String> = emptyList(),
        topicIds: List<String> = emptyList(),
    ) = FilterInput(scope = scope, key = "${scope.name}:1", author = author, text = text, topics = topics, topicIds = topicIds)

    @Test
    fun matchesTextRuleInsideOneWindow() {
        val match = engine(ruleSet(textRule())).match(input("这家店代刷好评很快"))
        assertNotNull(match)
        assertEquals("twilight:text.spam", match?.ruleId)
    }

    @Test
    fun rejectsTermsThatSitOutsideTheWindow() {
        // 24 characters apart: the two groups no longer describe one statement.
        assertNull(engine(ruleSet(textRule())).match(input("代刷" + "的".repeat(20) + "好评")))
    }

    @Test
    fun rejectsTermsSplitAcrossSentences() {
        assertNull(engine(ruleSet(textRule())).match(input("先说代刷。另外好评也多")))
    }

    @Test
    fun anExceptionSparesTheSentence() {
        val rule = textRule(exceptions = listOf("禁止"))
        assertNull(engine(ruleSet(rule)).match(input("禁止代刷好评")))
        assertNotNull(engine(ruleSet(rule)).match(input("可以代刷好评")))
    }

    @Test
    fun strictRulesOnlyRunInStrictMode() {
        val rule = textRule(level = "strict", groups = listOf(listOf("手机号"), listOf("转账")))
        assertNull(engine(ruleSet(rule)).match(input("把手机号给我，我转账给你")))
        assertNotNull(engine(ruleSet(rule), strict = true).match(input("把手机号给我，我转账给你")))
    }

    @Test
    fun authorRuleComparesTheUid() {
        val rule = TwilightRule(id = "author.1", reason = "营销号", kind = "author", values = listOf("123456"))
        assertNotNull(engine(ruleSet(rule)).match(input("正常内容", author = "123456")))
        assertNull(engine(ruleSet(rule)).match(input("正常内容", author = "654321")))
    }

    @Test
    fun topicRuleComparesNamesAndIds() {
        val rule = TwilightRule(id = "topic.1", reason = "刷单话题", kind = "topic", values = listOf("刷单"))
        assertNotNull(engine(ruleSet(rule)).match(input("内容", topics = listOf("刷单"))))
        assertNotNull(engine(ruleSet(rule)).match(input("内容", topicIds = listOf("刷单"))))
        assertNull(engine(ruleSet(rule)).match(input("内容", topics = listOf("闲聊"))))
    }

    @Test
    fun aDisabledRuleIsSkipped() {
        val set = ruleSet(textRule())
        assertNull(engine(set, disabled = setOf("twilight:text.spam")).match(input("这家店代刷好评很快")))
    }

    @Test
    fun scopesLimitWhereARuleRuns() {
        val set = ruleSet(textRule(scopes = listOf("comment")))
        assertNull(engine(set).match(input("这家店代刷好评很快", scope = FilterScope.Feed)))
        assertNotNull(engine(set).match(input("这家店代刷好评很快", scope = FilterScope.Comment)))
    }

    @Test
    fun malformedRuleSetsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TwilightRuleSet.parse(
                """{"schemaVersion":2,"revision":1,"publishedAt":"2026-01-02T03:04:05Z","rules":[]}""",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TwilightRuleSet.parse(
                """{"schemaVersion":1,"revision":1,"publishedAt":"2026-02-31T03:04:05Z","rules":[]}""",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            val rule = """{"id":"text.spam","reason":"测试","kind":"text","groups":[["代刷"]]}"""
            TwilightRuleSet.parse(
                """{"schemaVersion":1,"revision":1,"publishedAt":"2026-01-02T03:04:05Z","rules":[$rule,$rule]}""",
            )
        }
    }

    @Test
    fun broadTermsAndPlainHttpSourcesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ruleSet(textRule(groups = listOf(listOf("lsp"), listOf("好评")))).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ruleSet(
                TwilightRule(
                    id = "text.source",
                    reason = "测试规则",
                    kind = "text",
                    groups = listOf(listOf("代刷")),
                    sources = listOf("http://example.com/rules"),
                ),
            ).validate()
        }
    }

    @Test
    fun roundTripsThroughStorage() {
        val set = ruleSet(
            textRule(exceptions = listOf("禁止")),
            TwilightRule(id = "author.1", reason = "营销号", kind = "author", values = listOf("123456")),
        )
        val preferences = ContentFilterPreferences(
            enabled = true,
            twilight = set,
            twilightStrict = true,
            disabledTwilightRules = setOf("twilight:author.1"),
        )
        val decoded = decodeContentFilter(preferences.toJson().toString())
        assertEquals(set, decoded.twilight)
        assertEquals(true, decoded.twilightStrict)
        assertEquals(setOf("twilight:author.1"), decoded.disabledTwilightRules)
    }
}
