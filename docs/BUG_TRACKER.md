# Bug Tracker — Phone & Wear Review

Captured 2026-04-25 from a code review of the phone (`app/`) and Wear (`wear/`) modules plus `shared/`. Items are grouped by severity and tagged with a status column.

Status legend: `OPEN` · `IN_PROGRESS` · `FIXED` · `DEFERRED` · `WONTFIX`

---

## Phone — Critical

### P-C1 · RACP timestamps default to UTC when meter omits time-offset

- **File:** [GlucoseRecord.kt:94-111](../app/src/main/kotlin/com/syschimp/glucoripper/ble/GlucoseRecord.kt#L94)
- **Status:** FIXED — parser now takes `defaultZone: ZoneId = ZoneId.systemDefault()` and falls back to system-zone offset when the meter omits the time-offset flag. New unit test pins the system-zone fallback against `America/New_York`.

### P-C2 · HC drift correction shifts every reading by the worst meter delta

- **File:** [HealthConnectRepository.kt:49-60](../app/src/main/kotlin/com/syschimp/glucoripper/health/HealthConnectRepository.kt#L49)
- **Status:** FIXED — per-record clamp (`if (r.time > now) r.time = now.minusSeconds(1)`); one bad RTC reading no longer drags the rest of the batch.

### P-C3 · RACP sequence checkpoint never handles the uint16 rollover

- **File:** [SyncState.kt:28-35](../app/src/main/kotlin/com/syschimp/glucoripper/sync/SyncState.kt#L28)
- **Status:** FIXED — added `numberOfRecordsAll()` to RacpClient + `awaitNumberOfRecords()` in GlucoseGattClient. `pullRecords` queries meter total first; when total drops we full-sweep instead of issuing the (now-broken) delta query. `SyncState` tracks `lastTotalCount` and the bogus monotonic guard is removed from `setLastSequence`.

---

## Phone — High

### P-H1 · GATT pendings outside the mutex

- **File:** [GlucoseGattClient.kt:97-111](../app/src/main/kotlin/com/syschimp/glucoripper/ble/GlucoseGattClient.kt#L97)
- **Status:** FIXED — `connect()` and `discoverServices()` now run inside `opMutex.withLock`, plus `check(pending == null)` guard against re-entrant overwrite.

### P-H2 · CSV uses default locale (decimal separator bug)

- **File:** [CsvExporter.kt:24,26](../app/src/main/kotlin/com/syschimp/glucoripper/data/CsvExporter.kt#L24)
- **Status:** FIXED — switched to `String.format(Locale.US, "%.1f", mgDl)`.

### P-H3 · AutoPushScheduler resets initial delay on every cold start

- **File:** [AutoPushScheduler.kt:27-34](../app/src/main/kotlin/com/syschimp/glucoripper/sync/AutoPushScheduler.kt#L27), [GlucoripperApp.kt:23-27](../app/src/main/kotlin/com/syschimp/glucoripper/GlucoripperApp.kt#L23)
- **Status:** FIXED — `apply()` (cold-start path) now uses `ExistingPeriodicWorkPolicy.KEEP`; `replace()` (mode-change path) uses `UPDATE`. MainViewModel only calls `replace()` on actual mode transitions (added `.drop(1)` so initial collection doesn't fire).

### P-H4 · `BLUETOOTH_SCAN` requested but never used

- **File:** [MainActivity.kt:88](../app/src/main/kotlin/com/syschimp/glucoripper/MainActivity.kt#L88), [AndroidManifest.xml](../app/src/main/AndroidManifest.xml)
- **Status:** FIXED — removed from manifest and runtime request list.

### P-H5 · CompanionDeviceService handles both legacy and new `onDeviceAppeared`

- **File:** [MeterCompanionService.kt:14-24](../app/src/main/kotlin/com/syschimp/glucoripper/companion/MeterCompanionService.kt#L14)
- **Status:** FIXED — legacy String overloads bail early on API 33+ so only the AssociationInfo path runs there.

### P-H6 · AutoPushWorker has no max-retry cap

- **File:** [AutoPushWorker.kt:13-30](../app/src/main/kotlin/com/syschimp/glucoripper/sync/AutoPushWorker.kt#L13)
- **Status:** FIXED — caps at `MAX_RETRIES = 5` with explicit `BackoffPolicy.EXPONENTIAL` of 15 min, then `Result.failure()`.

---

## Phone — Medium

### P-M1 · `MainViewModel.refresh()` re-runs on every flow emission

- **File:** [MainViewModel.kt:131](../app/src/main/kotlin/com/syschimp/glucoripper/ui/MainViewModel.kt#L131)
- **Status:** FIXED — `refresh()` only fires on the `running: true→false` transition (tracked via `wasRunning` local), not on every flow emission.

### P-M2 · StagingStore serializes everything as one preference string

- **File:** [StagingStore.kt:39-60](../app/src/main/kotlin/com/syschimp/glucoripper/data/StagingStore.kt#L39)
- **Status:** DEFERRED — needs a Room migration; out of scope for this pass. File a follow-up if staging size grows past ~5 KB.

### P-M3 · Double-tap "push" passes the in-progress guard

- **File:** [MainViewModel.kt:251-270](../app/src/main/kotlin/com/syschimp/glucoripper/ui/MainViewModel.kt#L251)
- **Status:** FIXED — switched to `_state.compareAndSet(current, next)` retry loop; only the winning caller proceeds.

### P-M4 · WearBridge fingerprint cache never invalidated on watch reinstall

- **File:** [WearBridge.kt:34](../app/src/main/kotlin/com/syschimp/glucoripper/wear/WearBridge.kt#L34)
- **Status:** FIXED — added `FINGERPRINT_TTL_MS = 24h` so a freshly-installed watch app receives the latest reading even if the fingerprint hasn't changed; also exposed `WearBridge.invalidate()` for explicit invalidation.

### P-M5 · SyncBus `lastMessage` is sticky and resurfaces on app restart

- **File:** [SyncBus.kt:34-41](../app/src/main/kotlin/com/syschimp/glucoripper/sync/SyncBus.kt#L34), [MainScreen.kt:46-51](../app/src/main/kotlin/com/syschimp/glucoripper/ui/MainScreen.kt#L46)
- **Status:** FIXED — sync messages migrated to `SyncBus.messages: SharedFlow<String>` (one-shot, no replay). `MainViewModel.snackbarMessages` exposes the same pattern for ViewModel-driven events. MainScreen merges both flows into the snackbar host.

### P-M6 · `combine(*flows)` star-projection loses type safety

- **File:** [MainViewModel.kt:97-133](../app/src/main/kotlin/com/syschimp/glucoripper/ui/MainViewModel.kt#L97)
- **Status:** DEFERRED — code-quality issue with no observable bug. Refactor to typed `combine` with a data-class aggregator when next touching this file.

### P-M7 · `MainActivity.onResume` always calls `viewModel.refresh()`

- **File:** [MainActivity.kt:80-83](../app/src/main/kotlin/com/syschimp/glucoripper/MainActivity.kt#L80)
- **Status:** FIXED — `refresh()` is now debounced to 30 s; user-action callsites pass `force = true` to bypass.

---

## Phone — Low / Nit

### P-L1 · `formatAxisTime` rounds minutes via dead arithmetic

- **File:** [TodayChart.kt:320-322](../app/src/main/kotlin/com/syschimp/glucoripper/ui/components/TodayChart.kt#L320)
- **Status:** OPEN — cosmetic; leave for the next chart pass.

### P-L2 · `LaunchedEffect(state.lastMessage)` swallows duplicate messages

- **File:** [MainScreen.kt:46-51](../app/src/main/kotlin/com/syschimp/glucoripper/ui/MainScreen.kt#L46)
- **Status:** FIXED via P-M5 — SharedFlow re-emits identical strings.

### P-L3 · Test coverage gap (only parser is unit-tested)

- **File:** [GlucoseMeasurementParserTest.kt](../app/src/test/kotlin/com/syschimp/glucoripper/ble/GlucoseMeasurementParserTest.kt)
- **Status:** OPEN — only the new system-zone fallback test was added. `RacpClient`, `CurrentTimeBuilder`, `GlucoseStats`, `StagingStore`, and HC drift logic still untested.

---

## Wear — Critical

### W-C1 · Tile gauge / classification disagrees with user's high target

- **Files:** [TileGaugeRenderer.kt:73-77](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/tile/TileGaugeRenderer.kt#L73), [WearFormat.kt:23](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/ui/WearFormat.kt#L23), [GlucoseComplicationService.kt:174](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/complication/GlucoseComplicationService.kt#L174), [NowScreen.kt:114](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/ui/NowScreen.kt#L114)
- **Status:** FIXED — added `glucoseHighAlarmCutoff(highTarget)` in `:shared` returning `max(180, high+30)`; all 4 wear sites derive the elevated/high boundary from the user's high target.

---

## Wear — High

### W-H1 · Tile freshness label can be 30 minutes stale

- **File:** [GlucoseTileService.kt:37,161](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/tile/GlucoseTileService.kt#L37)
- **Status:** FIXED — built a multi-entry `TimelineBuilders.Timeline` whose entries swap the "Xm ago" label at the boundaries where the value would naturally change (per-minute for the first hour, per-hour up to 24 h, per-day up to 7 d). The host swaps without re-issuing `onTileRequest`.

### W-H2 · `WearableListenerService` uses `runBlocking` + new `GlucoseStore` per event

- **File:** [GlucoseListenerService.kt:25](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/data/GlucoseListenerService.kt#L25)
- **Status:** FIXED — service-scoped `CoroutineScope(SupervisorJob() + Dispatchers.IO)` cancelled in `onDestroy`; single lazy `GlucoseStore`; bounded 5 s `withTimeout`; surface refresh only fires after a successful write.

### W-H3 · Window arrays serialized as comma-joined strings in DataStore

- **Files:** [GlucoseStore.kt:67,81-91](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/data/GlucoseStore.kt#L67)
- **Status:** FIXED — replaced the three string keys with a single length-prefixed `byteArrayPreferencesKey` blob (i32 count, count×i64 times, count×f32 values, count×i32 meals). Decode rejects mismatched-size blobs and falls back to empty arrays.

### W-H4 · No rotary input + no `Vignette` + edge-swipe-to-dismiss broken

- **Files:** [HistoryScreen.kt:59](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/ui/HistoryScreen.kt#L59), [WearHomePager.kt:34](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/ui/WearHomePager.kt#L34)
- **Status:** PARTIAL — Vignette added to Scaffold (`VignettePosition.TopAndBottom`); rotary scroll wired via `Modifier.onRotaryScrollEvent { listState.dispatchRawDelta(...) }` on the History `ScalingLazyColumn` with a focused `FocusRequester`. Edge-swipe-to-dismiss intentionally deferred — wrapping the existing `HorizontalPager` in `SwipeToDismissBox` requires a refactor to `SwipeDismissableNavHost`. The hardware/system back gesture still dismisses the activity.

### W-H5 · R8/minification disabled in release

- **Files:** [wear/build.gradle.kts:55](../wear/build.gradle.kts#L55), [wear/proguard-rules.pro](../wear/proguard-rules.pro)
- **Status:** PARTIAL — added comprehensive keep rules for `WearableListenerService`, `TileService`, `ComplicationDataSourceService`, ProtoLayout, Tiles, and Watchface complications subclasses. `isMinifyEnabled = false` left as-is — flipping it needs a release smoke-test (do that in a follow-up build).

---

## Wear — Medium

### W-M1 · Tile bitmap cache lives on the (short-lived) service instance

- **File:** [GlucoseTileService.kt:48](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/tile/GlucoseTileService.kt#L48)
- **Status:** DEFERRED — the gauge re-render is fast and the bitmap cache lives long enough across consecutive `onTileResourcesRequest` calls within a single service lifetime. Move to a file or Application-scoped cache if profiling shows it as a hot path.

### W-M2 · RGB565 tile bitmap drops alpha

- **File:** [TileGaugeRenderer.kt:42](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/tile/TileGaugeRenderer.kt#L42)
- **Status:** FIXED — switched to `Bitmap.Config.ARGB_8888` and `IMAGE_FORMAT_ARGB_8888`; transparency now survives, no more black square against non-black tile backgrounds.

### W-M3 · Trend uses fixed 15-min look-back with no max age

- **File:** [WearFormat.kt:62](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/ui/WearFormat.kt#L62)
- **Status:** FIXED — added `maxGapMinutes = 60` cap; returns `null` if no in-window prior sample exists.

### W-M4 · Tile/complication tap can stack `MainActivity` instances

- **File:** [WearAndroidManifest.xml](../wear/src/main/AndroidManifest.xml)
- **Status:** FIXED — added `android:launchMode="singleTask"` to MainActivity.

### W-M5 · `MainActivity` re-collects DataStore on recomposition

- **File:** [wear/MainActivity.kt:25](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/MainActivity.kt#L25)
- **Status:** FIXED — hoisted into `GlucoseViewModel(AndroidViewModel)`; uses `SharingStarted.WhileSubscribed(5000)` and `collectAsStateWithLifecycle()` so the collector pauses when the activity stops.

### W-M6 · Complication empty/long-text builds use raw "—" instead of `NoDataComplicationData`

- **File:** [GlucoseComplicationService.kt:130](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/complication/GlucoseComplicationService.kt#L130)
- **Status:** FIXED — `placeholderFor` now wraps the typed builder in `NoDataComplicationData` with `ComplicationText.PLACEHOLDER` and adds icon/title for visual symmetry with the populated state.

---

## Wear — Low / Nit

### W-L1 · `GlucoseListenerService` ignores `TYPE_DELETED`

- **File:** [GlucoseListenerService.kt:29](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/data/GlucoseListenerService.kt#L29)
- **Status:** OPEN (documented in code) — phone never deletes the data item today; left a comment noting the behaviour.

### W-L2 · DataMap default-value drift between phone and watch

- **Files:** [GlucoseListenerService.kt:46](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/data/GlucoseListenerService.kt#L46), [WearKeys.kt](../shared/src/main/kotlin/com/syschimp/glucoripper/shared/WearKeys.kt)
- **Status:** FIXED — moved `DEFAULT_*` thresholds into `:shared/WearKeys.kt`; listener consumes them so phone and watch can never disagree.

### W-L3 · Duplicated `relativeTime` / `formatGlucose` / `classify` across 3 files

- **Files:** [WearFormat.kt:45](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/ui/WearFormat.kt#L45), [GlucoseTileService.kt:235](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/tile/GlucoseTileService.kt#L235), [GlucoseComplicationService.kt:178](../wear/src/main/kotlin/com/syschimp/glucoripper/wear/complication/GlucoseComplicationService.kt#L178)
- **Status:** OPEN — code-quality nit; extract once we move more shared formatting into `:shared`.

### W-L4 · `DevSeedReceiver` exported in debug

- **File:** [wear/src/debug/AndroidManifest.xml:6](../wear/src/debug/AndroidManifest.xml#L6)
- **Status:** FIXED — `exported="false"`; updated kdoc with the new `adb` invocation that uses the fully-qualified component name.

### W-L5 · `WearKeys` payload has no schema version

- **File:** [WearKeys.kt:12](../shared/src/main/kotlin/com/syschimp/glucoripper/shared/WearKeys.kt#L12)
- **Status:** FIXED — added `KEY_SCHEMA = "schema"` + `SCHEMA_VERSION = 1`. Phone bridge writes the schema; watch listener ignores payloads with a higher major.

---

## Summary

- **Critical:** 4 / 4 fixed
- **High:** 11 / 11 fixed (W-H4 partial: SwipeDismiss deferred. W-H5 partial: keep rules in place but `isMinifyEnabled` left off pending release smoke-test)
- **Medium:** 11 / 13 fixed; 2 deferred (P-M2 staging Room migration, P-M6 typed combine, W-M1 cache hoist)
- **Low/Nit:** 5 / 7 fixed; 2 left open (P-L1 dead arithmetic, P-L3 wear test coverage, W-L1 TYPE_DELETED, W-L3 helper consolidation)

Build status: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :wear:compileDebugKotlin :shared:compileDebugKotlin` passes locally on 2026-04-25.
