package com.syschimp.glucoripper.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private const val FRESHNESS_MINUTES = 30L
private const val GAUGE_RES_ID = "gauge"
private const val GAUGE_SIZE_PX = 300

class GlucoseTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

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
        // Bumping on every new reading forces the tile host to re-request the
        // gauge bitmap so it reflects the latest value.
        payload.latestTimeMillis.toString().ifBlank { "0" }

    private fun buildResources(
        requestedVersion: String,
        payload: GlucosePayload,
    ): ResourceBuilders.Resources {
        val builder = ResourceBuilders.Resources.Builder().setVersion(requestedVersion)
        if (payload.latestTimeMillis != 0L) {
            val pngBytes = TileGaugeRenderer.renderPng(payload, GAUGE_SIZE_PX)
            builder.addIdToImageMapping(
                GAUGE_RES_ID,
                ResourceBuilders.ImageResource.Builder()
                    .setInlineResource(
                        ResourceBuilders.InlineImageResource.Builder()
                            .setData(pngBytes)
                            .setWidthPx(GAUGE_SIZE_PX)
                            .setHeightPx(GAUGE_SIZE_PX)
                            .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                            .build(),
                    )
                    .build(),
            )
        }
        return builder.build()
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

    private fun emptyLayout(): LayoutElement =
        Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(launchAppModifier())
            .addContent(label("Glucoripper", 14f, 0xFFE1E3E6.toInt()))
            .addContent(spacerH(6))
            .addContent(label("Waiting for phone sync…", 12f, dimColor()))
            .build()

    private fun readingLayout(payload: GlucosePayload): LayoutElement {
        val ago = relativeTime(payload.latestInstant)
        val inner = Column.Builder()
            .setWidth(DimensionBuilders.wrap())
            .setHeight(DimensionBuilders.wrap())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Image.Builder()
                    .setWidth(dp(150f))
                    .setHeight(dp(150f))
                    .setResourceId(GAUGE_RES_ID)
                    .build(),
            )
            .addContent(spacerH(2))
            .addContent(
                label(
                    ago,
                    10f,
                    dimColor(),
                    weight = LayoutElementBuilders.FONT_WEIGHT_MEDIUM,
                ),
            )
            .build()

        return Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setModifiers(launchAppModifier())
            .addContent(inner)
            .build()
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
