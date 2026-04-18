# Glucoripper

An Android app that syncs blood-glucose readings from a Bluetooth glucose meter
directly to [Google Health Connect](https://health.google/health-connect-android/),
no vendor app required. Ships with a Wear OS companion that mirrors your
latest reading to the watch.

Originally built for the **Ascensia Contour Next One**, but works with any meter
that implements the standard Bluetooth **Glucose Profile** (service `0x1808`).

## What it does

- Pairs with the meter using Android's **CompanionDeviceManager**, so the OS
  wakes the app whenever the meter comes into Bluetooth range — no constant
  background scanning, no persistent foreground service.
- Reads stored readings via the standard **Glucose Profile + RACP** (Record
  Access Control Point). First sync pulls everything; subsequent syncs only
  pull new records using a per-meter sequence-number checkpoint.
- Parses **meal context** (before / after meal, fasting) from the Glucose
  Measurement Context characteristic and maps it to Health Connect's
  `relationToMeal` field.
- Pushes the phone's clock to the meter's **Current Time Service** on every
  sync so readings come back correctly timestamped even if the meter's RTC
  has drifted.
- Skips control-solution readings automatically.
- Writes idempotently — re-running a sync doesn't create duplicates (uses
  `clientRecordId = "$meterAddress/$sequenceNumber"`).
- **Stages new readings for review** before pushing to Health Connect, so
  you can annotate with meal context, feeling, or a note. Or enable auto-push
  to skip the review step entirely.

## Phone app

A four-tab Compose UI with locked branded theming (dynamic color off by
default so the medical content keeps a consistent palette).

- **Dashboard** — 24-hour glucose chart with the target band drawn in, latest
  reading overlaid with a band pill and trend arrow, quick-stats strip (in-range
  %, 7-day average, readings today), time-in-range card, and a recent-readings
  preview. Problem banners only appear when Bluetooth is off, Health Connect
  needs permissions, or the meter battery is low.
- **History** — full readings list grouped by day with per-day stats (average,
  in-range / low / high counts). Tap any row to edit meal relation, feeling,
  or note.
- **Devices** — paired-meter list with per-meter sync, full resync, unpair,
  and live Bluetooth / Health Connect status.
- **Settings** — units (mg/dL or mmol/L), target range, auto-push mode
  (off / after each sync), sync history log, CSV export.

## Wear OS companion

Designed as a **display-only** client — the phone keeps doing all the BLE work.
The watch just mirrors whatever the phone already has in Health Connect.

- **Main app** — latest reading with band color, trend arrow, time-ago, and a
  24-hour mini chart with the target band.
- **Tile** — swipeable home card showing the latest reading and a rendered
  24-hour bitmap chart. Taps launch the full app.
- **Complication** — publishes `SHORT_TEXT` (`138↗`), `LONG_TEXT`
  (`138 mg/dL ↗ · In range`), and `RANGED_VALUE` (arc showing position between
  40–300 mg/dL) for use on any watch face.

Data flows from phone → watch via the Wearable `DataClient`: after every
sync, the phone publishes a DataItem with the latest reading plus a rolling
24-hour window. The watch's `WearableListenerService` persists the payload
to DataStore and pokes the tile + every active complication to refresh. Typical
phone-sync-to-watch-update latency is a couple of seconds.

## Requirements

- Android **12 (API 31)** or newer
- Bluetooth LE
- [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata)
  installed (pre-installed on Android 14+)
- A compatible meter — see below
- _(Optional)_ Wear OS 3 or newer for the watch companion; tested on
  Samsung Galaxy Watch 8 (Wear OS 6).

## Compatible meters

Any finger-prick meter that implements the standard Bluetooth Glucose Profile.
Known working or expected to work:

- Ascensia Contour Next One / Contour Plus One ✓ tested
- Accu-Chek Guide / Instant / Aviva Connect
- OneTouch Verio Reflect / Flex
- Nipro TRUEyou, Beurer GL50evo, BeatO, iHealth, Agamatrix

**Not supported**: continuous glucose monitors (Dexcom, FreeStyle Libre 2/3,
Medtronic Guardian, Eversense) — these use proprietary, encrypted BLE
protocols rather than the standard Glucose Profile and would require
per-vendor reverse-engineering.

## Building

Clone the repo, open in Android Studio, hit **Run** against the `:app` module
for the phone or `:wear` for the watch. From the terminal:

```bash
# Phone (debug) — connect the phone via ADB
./gradlew :app:installDebug

# Watch (debug) — connect the Wear OS device via ADB over Wi-Fi
./gradlew :wear:installDebug

# Release bundles for Play Console upload
./gradlew :app:bundleRelease :wear:bundleRelease
```

Release signing is wired up to look for env vars (`KEYSTORE_FILE`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) first, then fall back to a
`keystore.properties` file in the repo root. Both locations are gitignored.

## Architecture

### `:app` (phone)

- [`ble/`](app/src/main/kotlin/com/syschimp/glucoripper/ble/) — Bluetooth
  Glucose Service client: UUID catalog, GATT state machine, RACP request /
  response, measurement + context parser (IEEE-11073 SFLOAT), Current Time
  Service clock push.
- [`companion/`](app/src/main/kotlin/com/syschimp/glucoripper/companion/) —
  CompanionDeviceManager pairing and the `CompanionDeviceService` that fires
  on proximity.
- [`sync/`](app/src/main/kotlin/com/syschimp/glucoripper/sync/) — short-lived
  foreground service, sync coordinator, per-meter sequence checkpoint store,
  staging store for pre-push readings, auto-push scheduler, and a
  single-flight bus so UI-triggered and proximity-triggered syncs don't race.
- [`health/`](app/src/main/kotlin/com/syschimp/glucoripper/health/) —
  Health Connect repository: writes `BloodGlucoseRecord`, reads recent
  readings, updates meal relation on existing records.
- [`data/`](app/src/main/kotlin/com/syschimp/glucoripper/data/) — DataStore
  wrappers for preferences, annotations (feeling / note / meal override),
  staged readings, and sync history.
- [`ui/`](app/src/main/kotlin/com/syschimp/glucoripper/ui/) — Jetpack Compose
  UI: four-tab `NavHost`, per-screen scaffolds, branded Material 3 theme,
  detail bottom sheets, time-in-range visualisation.
- [`wear/WearBridge.kt`](app/src/main/kotlin/com/syschimp/glucoripper/wear/WearBridge.kt) —
  the phone-side bridge: reads the latest Health Connect state plus prefs
  and publishes a `DataClient` DataItem. Fire-and-forget so Wearable failures
  never surface as sync failures.

### `:wear` (watch)

- [`data/GlucoseListenerService.kt`](wear/src/main/kotlin/com/syschimp/glucoripper/wear/data/GlucoseListenerService.kt) —
  `WearableListenerService` receiving `DataClient` payloads from the phone,
  persisting them to DataStore, and poking the tile + active complications
  to refresh.
- [`tile/`](wear/src/main/kotlin/com/syschimp/glucoripper/wear/tile/) —
  ProtoLayout tile that renders the 24h chart to a PNG in-process and serves
  it as an inline image resource. Resources version is bumped on every new
  reading so the tile host re-requests the bitmap.
- [`complication/`](wear/src/main/kotlin/com/syschimp/glucoripper/wear/complication/) —
  `SuspendingComplicationDataSourceService` exposing three complication types
  with trend-arrow computation from the rolling window.
- [`ui/`](wear/src/main/kotlin/com/syschimp/glucoripper/wear/ui/) — Compose for
  Wear full-app UI reading from the same DataStore.

## Distribution

Phone and watch share the same `applicationId` (`com.syschimp.glucoripper`) so
the Wearable `DataClient` can route between them. Play Console requires unique
versionCodes per applicationId, so the watch's `versionCode` is offset by
`1_000_000` to stay in lockstep with the phone version without colliding:
phone `N` pairs with wear `1_000_00N`.

Both are distributed via Play Console's **Internal testing** tracks — a
separate track for the phone and one for the Wear OS form factor, as required
by Google's Aug 2023 paired-app policy.

## License

[MIT](LICENSE).

## Disclaimer

This is a personal hobby project and not a medical device. It is not reviewed
or approved by any regulator. Do not use it as the sole source of truth for
medication dosing. Always cross-check with the readings displayed on your
meter.
