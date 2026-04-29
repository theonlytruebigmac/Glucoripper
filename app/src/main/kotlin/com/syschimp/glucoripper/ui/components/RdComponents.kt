package com.syschimp.glucoripper.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syschimp.glucoripper.ui.theme.GlucoseElevated
import com.syschimp.glucoripper.ui.theme.GlucoseInRange
import com.syschimp.glucoripper.ui.theme.GlucoseLow
import com.syschimp.glucoripper.ui.theme.RdMono
import com.syschimp.glucoripper.ui.theme.RdOverline
import com.syschimp.glucoripper.ui.theme.RdStatusSoft
import com.syschimp.glucoripper.ui.theme.RdText

// ─────────── Theme helpers ───────────

@Composable
fun isRdDark(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun rdSubtle(): Color = if (isRdDark()) RdText.SubtleDark else RdText.SubtleLight

@Composable
fun rdFaint(): Color = if (isRdDark()) RdText.FaintDark else RdText.FaintLight

@Composable
fun rdLowSoft(): Color = if (isRdDark()) RdStatusSoft.LowDark else RdStatusSoft.LowLight

@Composable
fun rdOkSoft(): Color = if (isRdDark()) RdStatusSoft.OkDark else RdStatusSoft.OkLight

@Composable
fun rdAmberSoft(): Color = if (isRdDark()) RdStatusSoft.AmberDark else RdStatusSoft.AmberLight

// ─────────── Overline ───────────

@Composable
fun RdOverlineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text.uppercase(),
        style = RdOverline,
        color = color,
        modifier = modifier,
    )
}

// ─────────── Status chip (pill with arrow + label) ───────────

enum class RdBand { Low, Ok, Amber }

fun classifyBand(mgDl: Double, lowMgDl: Double, highMgDl: Double): RdBand = when {
    mgDl < lowMgDl -> RdBand.Low
    mgDl > highMgDl -> RdBand.Amber
    else -> RdBand.Ok
}

fun trendArrow(deltaMgDl: Double?): String = when {
    deltaMgDl == null -> "→"
    deltaMgDl > 15 -> "↗"
    deltaMgDl < -15 -> "↘"
    else -> "→"
}

@Composable
fun RdStatusChip(
    band: RdBand,
    arrow: String? = null,
    label: String = when (band) {
        RdBand.Low -> "Low"
        RdBand.Ok -> "In range"
        RdBand.Amber -> "Elevated"
    },
) {
    val (bg, fg) = when (band) {
        RdBand.Low -> rdLowSoft() to GlucoseLow
        RdBand.Ok -> rdOkSoft() to GlucoseInRange
        RdBand.Amber -> rdAmberSoft() to GlucoseElevated
    }
    Row(
        modifier = Modifier
            .background(bg, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (arrow != null) {
            Text(
                arrow,
                style = RdMono.Label,
                fontWeight = FontWeight.SemiBold,
                color = fg,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = fg,
        )
    }
}

// ─────────── Banner (error / warn / info) ───────────

enum class RdBannerTone { Error, Warn, Info }

@Composable
fun RdBanner(
    tone: RdBannerTone,
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val (bg, fg, border) = when (tone) {
        RdBannerTone.Error -> Triple(rdLowSoft(), GlucoseLow, Color.Transparent)
        RdBannerTone.Warn -> Triple(rdAmberSoft(), GlucoseElevated, Color.Transparent)
        RdBannerTone.Info -> Triple(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.outline,
        )
    }
    val borderMod = if (tone == RdBannerTone.Info)
        Modifier.border(1.dp, border, RoundedCornerShape(12.dp))
    else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .then(borderMod)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon, contentDescription = null,
            tint = fg, modifier = Modifier.size(18.dp).padding(top = 1.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (tone == RdBannerTone.Info) MaterialTheme.colorScheme.onSurface else fg,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            Box(
                Modifier
                    .border(1.dp, fg, CircleShape)
                    .clickable { onAction() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = fg,
                )
            }
        }
    }
}

// ─────────── Indeterminate sync progress ───────────

@Composable
fun RdSyncProgress(label: String = "Syncing readings…") {
    val transition = rememberInfiniteTransition(label = "rdProgress")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rdProgressOffset",
    )
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "·  ·  ·",
                style = RdMono.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.4f)
                    .graphicsLayer {
                        translationX = progress * size.width
                    }
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

// ─────────── Time-in-range stacked bar ───────────

@Composable
fun RdTIRBar(
    low: Int,
    ok: Int,
    amber: Int,
    high: Int,
    showLabels: Boolean = true,
) {
    val total = (low + ok + amber + high).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        ) {
            if (low > 0) Box(
                Modifier
                    .fillMaxHeight()
                    .weight(low.toFloat())
                    .background(GlucoseLow),
            )
            if (ok > 0) Box(
                Modifier
                    .fillMaxHeight()
                    .weight(ok.toFloat())
                    .background(GlucoseInRange),
            )
            if (amber > 0) Box(
                Modifier
                    .fillMaxHeight()
                    .weight(amber.toFloat())
                    .background(GlucoseElevated),
            )
            if (high > 0) Box(
                Modifier
                    .fillMaxHeight()
                    .weight(high.toFloat())
                    .background(GlucoseLow),
            )
        }
        if (showLabels) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TIRLegend(pct = low * 100 / total, label = "low", color = GlucoseLow)
                TIRLegend(pct = ok * 100 / total, label = "in target", color = GlucoseInRange)
                TIRLegend(pct = amber * 100 / total, label = "elev", color = GlucoseElevated)
                TIRLegend(pct = high * 100 / total, label = "high", color = GlucoseLow)
            }
        }
    }
}

@Composable
private fun TIRLegend(pct: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$pct%",
            style = RdMono.Caption,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────── Stat card (label + mono value + optional unit) ───────────

@Composable
fun RdStatCard(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            label.uppercase(),
            style = RdOverline.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = RdMono.Stat,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (unit != null) {
                Text(
                    unit,
                    style = RdMono.Tiny,
                    color = rdSubtle(),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
        }
    }
}

// ─────────── Banded reading row (band stripe + time + meal + value + chevron) ───────────

@Composable
fun RdReadingRow(
    bandColor: Color,
    timeLabel: String,
    mealLabel: String,
    mgDlText: String,
    unitText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(28.dp)
                .background(bandColor, RoundedCornerShape(4.dp)),
        )
        Text(
            timeLabel,
            style = RdMono.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(
            mealLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                mgDlText,
                style = RdMono.Row,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                unitText,
                style = RdMono.Tiny,
                color = rdSubtle(),
                modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = rdSubtle(),
            modifier = Modifier.size(14.dp),
        )
    }
}

// ─────────── Range selector (segmented chip row, used above the chart) ───────────

@Composable
fun RdRangeSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(8.dp),
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { o ->
            val isSel = o == selected
            Box(
                Modifier
                    .clickable { onSelect(o) }
                    .background(
                        if (isSel) MaterialTheme.colorScheme.surfaceContainerHigh
                        else Color.Transparent,
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    o,
                    style = RdMono.Caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSel) MaterialTheme.colorScheme.onSurface else rdSubtle(),
                )
            }
        }
    }
}

// ─────────── Segmented control (Settings: units, theme) ───────────

data class RdSegmentedOption(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
)

@Composable
fun RdSegmented(
    options: List<RdSegmentedOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(10.dp),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { opt ->
            val isSel = opt.key == selected
            Row(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(opt.key) }
                    .background(
                        if (isSel) MaterialTheme.colorScheme.surfaceContainerHigh
                        else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (opt.icon != null) {
                    Icon(
                        opt.icon, contentDescription = null,
                        tint = if (isSel) MaterialTheme.colorScheme.onSurface else rdSubtle(),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    opt.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (isSel) MaterialTheme.colorScheme.onSurface else rdSubtle(),
                )
            }
        }
    }
}

// ─────────── Section header (RdOverline + optional trailing element) ───────────

@Composable
fun RdSectionHeader(
    overline: String,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RdOverlineText(overline)
        trailing?.invoke()
    }
}

// ─────────── Empty state (dashed-border card) ───────────

@Composable
fun RdEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 32.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .clickable { onAction() }
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape,
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

// ─────────── Hairline rule ───────────

@Composable
fun RdHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

