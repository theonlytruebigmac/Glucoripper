package com.syschimp.glucoripper.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.syschimp.glucoripper.wear.MainActivity
import com.syschimp.glucoripper.wear.data.GlucosePayload
import com.syschimp.glucoripper.wear.data.GlucoseStore
import com.syschimp.glucoripper.wear.data.GlucoseUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private const val FRESHNESS_MINUTES = 30L
private const val CHART_RES_ID = "chart"
private const val CHART_WIDTH_PX = 336
private const val CHART_HEIGHT_PX = 112

class GlucoseTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                runCatching { GlucoseStore(applicationContext).flow.first() }
                    .onSuccess { payload -> completer.set(buildTile(payload)) }
                    .onFailure { completer.setException(it) }
            }
            "glucose-tile-request"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                runCatching { GlucoseStore(applicationContext).flow.first() }
                    .onSuccess { payload ->
                        completer.set(buildResources(requestParams.version, payload))
                    }
                    .onFailure { completer.setException(it) }
            }
            "glucose-tile-resources"
        }

    private fun resourcesVersion(payload: GlucosePayload): String =
        // Bumping the version on every new reading forces the tile host to re-request
        // resources so the bitmap chart stays fresh; a static "1" would cache forever.
        payload.latestTimeMillis.toString().ifBlank { "0" }

    private fun buildResources(
        requestedVersion: String,
        payload: GlucosePayload,
    ): ResourceBuilders.Resources {
        val pngBytes = TileChartRenderer.renderPng(payload, CHART_WIDTH_PX, CHART_HEIGHT_PX)
        return ResourceBuilders.Resources.Builder()
            .setVersion(requestedVersion)
            .addIdToImageMapping(
                CHART_RES_ID,
                ResourceBuilders.ImageResource.Builder()
                    .setInlineResource(
                        ResourceBuilders.InlineImageResource.Builder()
                            .setData(pngBytes)
                            .setWidthPx(CHART_WIDTH_PX)
                            .setHeightPx(CHART_HEIGHT_PX)
                            .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun buildTile(payload: GlucosePayload): TileBuilders.Tile {
        val layout = if (payload.latestTimeMillis == 0L) emptyLayout() else readingLayout(payload)
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(resourcesVersion(payload))
            .setFreshnessIntervalMillis(Duration.ofMinutes(FRESHNESS_MINUTES).toMillis())
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(layout)
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun emptyLayout(): LayoutElementBuilders.LayoutElement =
        Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(launchAppModifier())
            .addContent(label("Glucoripper", 14f, 0xFFE1E3E6.toInt()))
            .addContent(spacerH(6))
            .addContent(label("Waiting for phone sync…", 12f, dimColor()))
            .build()

    private fun readingLayout(payload: GlucosePayload): LayoutElementBuilders.LayoutElement {
        val mgDl = payload.latestMgDl
        val bandColor = bandColorArgb(mgDl, payload.targetLowMgDl, payload.targetHighMgDl)
        val bandLabel = classifyLabel(mgDl, payload.targetLowMgDl, payload.targetHighMgDl)
        val valueText = formatValue(mgDl, payload.unit)
        val unitText = unitLabel(payload.unit)
        val arrow = trendArrow(payload)
        val ago = relativeTime(payload.latestInstant)

        val builder = Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(launchAppModifier())
            .addContent(
                Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
                    .addContent(
                        label(
                            valueText,
                            34f,
                            bandColor,
                            weight = LayoutElementBuilders.FONT_WEIGHT_BOLD,
                        ),
                    )
                    .addContent(spacerW(3))
                    .addContent(label(unitText, 10f, dimColor()))
                    .apply {
                        if (arrow.isNotEmpty()) {
                            addContent(spacerW(4))
                            addContent(
                                label(
                                    arrow,
                                    14f,
                                    bandColor,
                                    weight = LayoutElementBuilders.FONT_WEIGHT_MEDIUM,
                                ),
                            )
                        }
                    }
                    .build(),
            )
            .addContent(spacerH(2))
            .addContent(
                label(
                    "$bandLabel · $ago",
                    10f,
                    dimColor(),
                    weight = LayoutElementBuilders.FONT_WEIGHT_MEDIUM,
                ),
            )

        if (payload.windowMgDls.size >= 2) {
            builder.addContent(spacerH(6))
            builder.addContent(
                Image.Builder()
                    .setWidth(dp((CHART_WIDTH_PX / 2).toFloat()))
                    .setHeight(dp((CHART_HEIGHT_PX / 2).toFloat()))
                    .setResourceId(CHART_RES_ID)
                    .build(),
            )
        }

        return builder.build()
    }

    private fun label(
        text: String,
        sizeSp: Float,
        color: Int,
        weight: Int = LayoutElementBuilders.FONT_WEIGHT_NORMAL,
    ): Text =
        Text.Builder()
            .setText(text)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(sizeSp))
                    .setColor(argb(color))
                    .setWeight(weight)
                    .build(),
            )
            .build()

    private fun spacerH(height: Int): Spacer =
        Spacer.Builder().setHeight(dp(height.toFloat())).build()

    private fun spacerW(width: Int): Spacer =
        Spacer.Builder().setWidth(dp(width.toFloat())).build()

    private fun launchAppModifier(): ModifiersBuilders.Modifiers =
        ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId("open_app")
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder()
                                    .setClassName(MainActivity::class.java.name)
                                    .setPackageName(applicationContext.packageName)
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun dimColor(): Int = 0xFFBFC8CD.toInt()

    private fun bandColorArgb(mgDl: Double, low: Double, high: Double): Int = when {
        mgDl <= 0 -> 0xFFBFC8CD.toInt()
        mgDl < low -> 0xFFE5484D.toInt()
        mgDl <= high -> 0xFF30A46C.toInt()
        mgDl < 180 -> 0xFFF5A524.toInt()
        else -> 0xFFE5484D.toInt()
    }

    private fun classifyLabel(mgDl: Double, low: Double, high: Double): String = when {
        mgDl <= 0 -> "—"
        mgDl < low -> "Low"
        mgDl <= high -> "In range"
        mgDl < 180 -> "Elevated"
        else -> "High"
    }

    private fun trendArrow(payload: GlucosePayload, minGapMinutes: Long = 15): String {
        val v = payload.windowMgDls
        val t = payload.windowTimesMillis
        if (v.size < 2) return ""
        val latestIdx = v.size - 1
        val gapMs = minGapMinutes * 60_000L
        var priorIdx = -1
        for (i in latestIdx - 1 downTo 0) {
            if (t[latestIdx] - t[i] >= gapMs) { priorIdx = i; break }
        }
        if (priorIdx < 0) return ""
        val delta = v[latestIdx] - v[priorIdx]
        return when {
            delta > 15f -> "↗"
            delta < -15f -> "↘"
            else -> "→"
        }
    }

    private fun formatValue(mgDl: Double, unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_PER_DL -> "%.0f".format(mgDl)
        GlucoseUnit.MMOL_PER_L -> "%.1f".format(mgDl / 18.0)
    }

    private fun unitLabel(unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_PER_DL -> "mg/dL"
        GlucoseUnit.MMOL_PER_L -> "mmol/L"
    }

    private fun relativeTime(t: Instant): String {
        val d = Duration.between(t, Instant.now())
        return when {
            d.isNegative -> "just now"
            d.toMinutes() < 1 -> "just now"
            d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
            d.toHours() < 24 -> "${d.toHours()}h ago"
            d.toDays() < 7 -> "${d.toDays()}d ago"
            else -> "> 1w"
        }
    }
}
