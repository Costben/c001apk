package app.awavenue.c001apk.data

/**
 * How long the custom haptic pulse runs. Persisted by [name], so renaming an entry resets the
 * setting instead of silently landing on another strength.
 */
enum class HapticStrength(val label: String, val durationMs: Long) {
    Light("轻", 8L),
    Standard("标准", 20L),
    Strong("强", 40L);

    /** The strength that actually fires. Reversing only swaps the two ends, never the middle. */
    fun effective(reverse: Boolean): HapticStrength = when {
        !reverse -> this
        this == Light -> Strong
        this == Strong -> Light
        else -> this
    }

    companion object {
        fun fromName(name: String?): HapticStrength =
            entries.firstOrNull { it.name == name } ?: Standard
    }
}
