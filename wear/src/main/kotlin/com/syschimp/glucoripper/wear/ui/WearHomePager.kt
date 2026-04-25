package com.syschimp.glucoripper.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.HorizontalPageIndicator
import androidx.wear.compose.material.PageIndicatorState
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.syschimp.glucoripper.wear.data.GlucosePayload

/**
 * Top-level watch UI. Two swipeable pages:
 *  0 — Now: ring gauge of the latest reading (mirrors the phone's RingGauge).
 *  1 — History: scrollable list of the last 7 days of readings.
 *
 * Data-only — no sync triggers on-wrist; the phone drives BLE sync.
 */
@Composable
fun WearHomePager(payload: GlucosePayload) {
    val pagerState = rememberPagerState(initialPage = 0) { PAGE_COUNT }
    val pageIndicatorState = remember {
        object : PageIndicatorState {
            override val pageOffset: Float get() = pagerState.currentPageOffsetFraction
            override val selectedPage: Int get() = pagerState.currentPage
            override val pageCount: Int get() = PAGE_COUNT
        }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        pageIndicator = {
            HorizontalPageIndicator(pageIndicatorState = pageIndicatorState)
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> NowScreen(payload)
                    1 -> HistoryScreen(payload)
                }
            }
        }
    }
}

private const val PAGE_COUNT = 2
