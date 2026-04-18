# Glucoripper

An Android app that syncs blood-glucose readings from a Bluetooth glucose meter
directly to [Google Health Connect](https://health.google/health-connect-android/),
no vendor app required.

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

## Features

- Dashboard with latest reading, time-in-range %, color-coded target band,
  daily-average sparkline.
- Tap any reading to edit **meal relation** (syncs back to Health Connect),
  add a **feeling** (local), or add a **note** (local).
- Toggle display units between **mg/dL** and **mmol/L**.
- Set your own **target range** for time-in-range calculations.
- **CSV export** with mg/dL, mmol/L, meal, specimen, and source ID columns.
- Sync history log with success/failure counts.
- Low-battery alert when the meter reports the flag in its status annunciation.

## Requirements

- Android **12 (API 31)** or newer
- Bluetooth LE
- [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata)
  installed (pre-installed on Android 14+)
- A compatible meter — see below

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

Clone the repo, open in Android Studio, hit **Run**. Or from the terminal:

```bash
./gradlew installDebug
```

The first Gradle sync will download AGP, Kotlin, Compose, and the Health
Connect SDK.

## Architecture

- [`app/src/main/kotlin/com/syschimp/glucoripper/ble/`](app/src/main/kotlin/com/syschimp/glucoripper/ble/) — Bluetooth
  Glucose Service client: UUID catalog, GATT state machine, RACP request /
  response, measurement + context parser (IEEE-11073 SFLOAT), Current Time
  Service clock push.
- [`companion/`](app/src/main/kotlin/com/syschimp/glucoripper/companion/) —
  CompanionDeviceManager pairing and the `CompanionDeviceService` that fires
  on proximity.
- [`sync/`](app/src/main/kotlin/com/syschimp/glucoripper/sync/) — short-lived
  foreground service, sync coordinator, per-meter sequence checkpoint store,
  and a single-flight bus so UI-triggered and proximity-triggered syncs don't
  race.
- [`health/`](app/src/main/kotlin/com/syschimp/glucoripper/health/) —
  Health Connect repository: writes `BloodGlucoseRecord`, reads recent
  readings, updates meal relation on existing records.
- [`data/`](app/src/main/kotlin/com/syschimp/glucoripper/data/) — DataStore
  wrappers for preferences, annotations (feeling / note / meal override),
  and sync history.
- [`ui/`](app/src/main/kotlin/com/syschimp/glucoripper/ui/) — Jetpack Compose
  UI: dashboard, bottom sheets (detail / settings / history),
  time-in-range card with sparkline.

## License

[MIT](LICENSE).

## Disclaimer

This is a hobby project, not a medical device. It is not reviewed or approved
by any regulator. Do not use it as the sole source of truth for medication
dosing. Always cross-check with the readings displayed on your meter.
