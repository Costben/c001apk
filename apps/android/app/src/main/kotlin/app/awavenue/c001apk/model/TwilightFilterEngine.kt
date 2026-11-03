package app.awavenue.c001apk.model

/**
 * The remote 秋风 rules, evaluated beside the user's own. Matching is literal: a text rule needs
 * every one of its groups inside one bounded window of one sentence, and any exception in that
 * sentence spares it. No remote regex is ever run.
 */
internal class TwilightFilterEngine(private val preferences: ContentFilterPreferences) {
    private data class Compiled(val rule: TwilightRule) {
        val groups = rule.groups.map { group -> group.map(::normalizeFilterText) }
        val exceptions = rule.exceptions.map(::normalizeFilterText)
    }

    private val rules: List<Compiled> = preferences.twilight?.rules.orEmpty()
        .filter { it.key !in preferences.disabledTwilightRules }
        .map(::Compiled)

    val isEnabled: Boolean get() = rules.isNotEmpty()

    fun match(input: FilterInput): ContentFilterMatch? {
        if (rules.isEmpty()) return null
        val sentences by lazy {
            var text = visibleFilterText(input.text)
            EXCLUDED_TERMS.forEach { text = text.replace(it, " ".repeat(it.length)) }
            text.split(SentenceBoundary)
        }
        for (compiled in rules) {
            val rule = compiled.rule
            if (input.scope.name.lowercase() !in rule.scopes ||
                (rule.level == "strict" && !preferences.twilightStrict)
            ) {
                continue
            }
            val matched = when (rule.kind) {
                "author" -> input.author in rule.values
                "topic" -> rule.values.any { it in input.topics || it in input.topicIds }
                "text" -> sentences.any { sentence ->
                    compiled.exceptions.none(sentence::contains) &&
                        matchesGroups(sentence, compiled.groups, rule.maxDistance)
                }

                else -> false
            }
            if (matched) return ContentFilterMatch(rule.key, "秋风 · ${rule.reason}")
        }
        return null
    }

    private companion object {
        val SentenceBoundary = Regex("[。！？!?；;\\r\\n]+")
    }
}

private data class Occurrence(val start: Int, val end: Int, val group: Int)

/** True when every group has a term in [sentence] and all picks fit inside [distance] characters. */
internal fun matchesGroups(sentence: String, groups: List<List<String>>, distance: Int): Boolean {
    if (groups.isEmpty() || groups.any { group -> group.none(sentence::contains) }) return false
    val hits = mutableListOf<Occurrence>()
    groups.forEachIndexed { index, terms ->
        terms.forEach { term ->
            var start = sentence.indexOf(term)
            while (start >= 0) {
                hits += Occurrence(start, start + term.length, index)
                start = sentence.indexOf(term, start + 1)
            }
        }
    }
    val latest = IntArray(groups.size) { -1 }
    for (hit in hits.sortedBy { it.end }) {
        latest[hit.group] = maxOf(latest[hit.group], hit.start)
        val earliest = latest.min()
        if (earliest >= 0 && hit.end - earliest <= distance) return true
    }
    return false
}
