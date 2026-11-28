package app.awavenue.c001apk.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.common.rememberSquircleShape
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.ProgressIndicatorDefaults
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Keeps submitted vote states synchronized between feed detail pages and list preview cards.
 */
internal object VoteActions {
    private val overrides = mutableStateMapOf<String, Entity>()

    fun voteFor(feed: Entity): Entity? = overrides[feed.id] ?: feed.vote

    fun rememberVote(feedId: String, vote: Entity) {
        if (feedId.isNotEmpty()) overrides[feedId] = vote
    }
}

internal fun Entity.voteOptions(): List<Entity> =
    list("options")
        .filter { it.id.isNotEmpty() && it.title.isNotEmpty() }
        .distinctBy { it.id }
        .sortedBy { it.int("order") ?: Int.MAX_VALUE }

internal fun Entity.voteSelectedIds(): Set<String> =
    string("user_selected_option")
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

internal fun Entity.voteSelectionRange(): IntRange {
    val min = (int("min_select_num") ?: 1).coerceAtLeast(1)
    val max = (int("max_select_num") ?: 1).coerceAtLeast(min)
    return min..max
}

internal fun Entity.isVoteUpcoming(nowSeconds: Long): Boolean =
    long("start_time")?.let { nowSeconds < it } == true

internal fun Entity.isVoteClosed(nowSeconds: Long): Boolean =
    long("end_time")?.let { nowSeconds >= it } == true

@Composable
internal fun rememberVoteNowSeconds(vote: Entity?): Long {
    val startTime = vote?.long("start_time")
    val endTime = vote?.long("end_time")
    val now by produceState(System.currentTimeMillis() / 1_000, startTime, endTime) {
        value = System.currentTimeMillis() / 1_000
        for (boundary in listOfNotNull(startTime, endTime).sorted()) {
            val remaining = boundary * 1_000 - System.currentTimeMillis()
            if (remaining > 0) delay(remaining)
            value = System.currentTimeMillis() / 1_000
        }
    }
    return now
}

@Composable
internal fun FeedVoteContent(
    vote: Entity?,
    title: String?,
    canVote: Boolean,
    submitting: Boolean,
    refreshing: Boolean,
    error: String?,
    onSubmit: (Set<String>, Boolean) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider()
        Text(
            text = title ?: "投票选项",
            style = COUITheme.textStyles.body1.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        )
        val options = vote?.voteOptions().orEmpty()
        if (vote == null || options.isEmpty()) {
            VoteSummary("暂无投票选项")
            HorizontalDivider()
            return@Column
        }

        val now = rememberVoteNowSeconds(vote)
        val closed = vote.isVoteClosed(now)
        val upcoming = vote.isVoteUpcoming(now)
        val votedIds = vote.voteSelectedIds()
        val showResults = closed || votedIds.isNotEmpty()
        val limits = vote.voteSelectionRange()
        val multiSelect = limits.last > 1
        val canSelect = canVote && !upcoming && !showResults && vote.id.isNotEmpty() &&
            !limits.isEmpty() && options.size >= limits.first
        var selection by rememberSaveable(vote.id) { mutableStateOf(arrayListOf<String>()) }
        var anonymous by rememberSaveable(vote.id) { mutableStateOf(false) }
        val selected = options.map { it.id }.filter(selection::contains).toSet()

        if (showResults) {
            val totalSelections = options.sumOf { option ->
                option.long("total_select_num")?.coerceAtLeast(0) ?: 0L
            }
            for (option in options) {
                val count = option.long("total_select_num")?.takeIf { it >= 0 }
                val fraction = count?.let { if (totalSelections > 0) it.toFloat() / totalSelections else 0f }
                val accent = parseHexColor(option.string("color")) ?: COUITheme.colorScheme.primary
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.title, Modifier.weight(1f))
                        if (option.id in votedIds) VoteSummary("已选")
                        if (count != null && fraction != null) {
                            VoteSummary("${(fraction * 100).roundToInt()}% · ${count}票")
                        }
                    }
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = fraction.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = accent,
                            ),
                            height = 5.dp,
                        )
                    }
                }
            }
        } else {
            if (canSelect && multiSelect) {
                VoteSummary(
                    if (limits.first == limits.last) "请选择 ${limits.last} 项"
                    else "请选择 ${limits.first}–${limits.last} 项",
                )
            }
            Column(
                modifier = if (multiSelect) Modifier else Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in options) {
                    val optionId = option.id
                    VoteChoice(
                        label = option.title,
                        selected = optionId in selected,
                        multiSelect = multiSelect,
                        enabled = canSelect && !submitting && !refreshing &&
                            (!multiSelect || optionId in selected || selected.size < limits.last),
                        onToggle = { checked ->
                            selection = if (!multiSelect) {
                                arrayListOf(optionId)
                            } else {
                                ArrayList(if (checked) selected + optionId else selected - optionId)
                            }
                        },
                    )
                }
            }
        }

        val status = when {
            closed -> "投票已结束"
            upcoming -> "投票尚未开始"
            votedIds.isNotEmpty() -> "已参与投票"
            !canVote -> "登录后参与投票"
            !canSelect -> "当前不可投票"
            else -> null
        }
        if (canSelect && vote.int("anonymous_status") == 1) {
            AnonymousVoteSwitch(
                checked = anonymous,
                enabled = !submitting && !refreshing,
                onCheckedChange = { anonymous = it },
            )
        }
        val participation = vote.int("total_vote_num")?.let { "${it}人参与" }
        listOfNotNull(participation, status)
            .joinToString(" · ")
            .takeIf(String::isNotEmpty)
            ?.let { VoteSummary(it) }
        if (canSelect) {
            error?.let {
                Text(
                    it,
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.error,
                )
            }
            TextButton(
                text = if (submitting) "提交中…" else "投票",
                onClick = { onSubmit(selected, anonymous) },
                enabled = !submitting && !refreshing && selected.size in limits,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                cornerRadius = 12.dp,
                minHeight = 44.dp,
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        HorizontalDivider()
    }
}

@Composable
internal fun FeedVotePreview(
    feed: Entity,
    vote: Entity,
    modifier: Modifier = Modifier,
) {
    val options = vote.voteOptions()
    if (options.isEmpty()) return
    val now = rememberVoteNowSeconds(vote)
    val closed = vote.isVoteClosed(now)
    val upcoming = vote.isVoteUpcoming(now)
    val votedIds = vote.voteSelectedIds()
    val showResults = closed || votedIds.isNotEmpty()
    val voteTitle = vote.string("message_title").takeIf {
        it.isNotEmpty() && it != feed.messageTitle && it != feed.title
    }
    val limits = vote.voteSelectionRange()
    val totalSelections = options.sumOf { option ->
        option.long("total_select_num")?.coerceAtLeast(0) ?: 0L
    }
    val shape = rememberSquircleShape(12.dp)

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(COUITheme.colorScheme.surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (voteTitle != null) {
            Text(
                voteTitle,
                style = COUITheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val previewOptions = options.take(4)
        for (option in previewOptions) {
            val count = option.long("total_select_num")?.takeIf { it >= 0 }
            val fraction = if (showResults && count != null) {
                if (totalSelections > 0) count.toFloat() / totalSelections else 0f
            } else {
                null
            }
            val optionShape = rememberSquircleShape(8.dp)
            val accent = parseHexColor(option.string("color")) ?: COUITheme.colorScheme.primary
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(optionShape)
                    .background(
                        if (option.id in votedIds) {
                            accent.copy(alpha = 0.08f)
                        } else {
                            COUITheme.colorScheme.surfaceContainer
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (option.id in votedIds) {
                            accent.copy(alpha = 0.45f)
                        } else {
                            COUITheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        },
                        shape = optionShape,
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option.title,
                        modifier = Modifier.weight(1f),
                        style = COUITheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (option.id in votedIds) {
                        Text(
                            "已选",
                            style = COUITheme.textStyles.footnote2,
                            color = accent,
                        )
                    }
                    if (fraction != null && count != null) {
                        Text(
                            "${(fraction * 100).roundToInt()}%",
                            style = COUITheme.textStyles.footnote2,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = fraction.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = accent),
                        height = 3.dp,
                    )
                }
            }
        }
        if (options.size > previewOptions.size) {
            Text(
                "还有 ${options.size - previewOptions.size} 个选项…",
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        val statusText = when {
            closed -> "已结束"
            upcoming -> "未开始"
            votedIds.isNotEmpty() -> "已投票"
            limits.last > 1 -> "多选（最多${limits.last}项） · 点开投票"
            else -> "单选 · 点开投票"
        }
        val meta = listOfNotNull(
            vote.int("total_vote_num")?.let { "${it}人参与" },
            statusText,
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            VoteSummary(meta)
        }
    }
}

@Composable
private fun VoteChoice(
    label: String,
    selected: Boolean,
    multiSelect: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colors = COUITheme.colorScheme
    val shape = rememberSquircleShape(12.dp)
    val row = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .clip(shape)
        .background(if (selected) colors.primary.copy(alpha = 0.09f) else colors.onSurface.copy(alpha = 0.025f))
        .border(
            width = 1.dp,
            color = if (selected) colors.primary.copy(alpha = 0.8f) else colors.onSurface.copy(alpha = 0.17f),
            shape = shape,
        )
    val interaction = if (multiSelect) {
        row.toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle)
    } else {
        row.selectable(selected, enabled = enabled, role = Role.RadioButton) { onToggle(true) }
    }
    Row(
        modifier = interaction.padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            Modifier.weight(1f),
            color = if (enabled || selected) colors.onSurface else colors.onSurfaceVariantSummary,
        )
        VoteChoiceIndicator(checked = selected, multiSelect = multiSelect, enabled = enabled)
    }
}

@Composable
private fun VoteChoiceIndicator(checked: Boolean, multiSelect: Boolean, enabled: Boolean) {
    val colors = COUITheme.colorScheme
    val indicator = when {
        checked -> colors.primary
        enabled -> colors.onSurface.copy(alpha = 0.53f)
        else -> colors.onSurface.copy(alpha = 0.3f)
    }
    val checkColor = colors.onPrimary
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.5.dp.toPx()
        val inset = stroke / 2
        if (multiSelect) {
            drawRoundRect(
                color = indicator,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = if (checked) Fill else Stroke(stroke),
            )
            if (checked) {
                val check = Path().apply {
                    moveTo(size.width * 0.26f, size.height * 0.51f)
                    lineTo(size.width * 0.43f, size.height * 0.67f)
                    lineTo(size.width * 0.75f, size.height * 0.34f)
                }
                drawPath(
                    check,
                    checkColor,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        } else {
            drawCircle(indicator, radius = size.minDimension / 2 - inset, style = Stroke(stroke))
            if (checked) drawCircle(indicator, radius = size.minDimension * 0.25f)
        }
    }
}

@Composable
private fun AnonymousVoteSwitch(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("匿名投票", Modifier.weight(1f), style = COUITheme.textStyles.body2)
            Spacer(Modifier.size(8.dp))
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
private fun VoteSummary(text: String) {
    Text(
        text,
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.onSurfaceVariantSummary,
    )
}

private fun parseHexColor(raw: String): Color? {
    val clean = raw.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    val parsed = clean.toLongOrNull(16) ?: return null
    val argb = if (clean.length == 6) (0xFF000000L or parsed) else parsed
    return Color(argb.toInt())
}
