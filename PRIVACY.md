# Privacy Policy — Glucoripper

_Last updated: 2026-04-18_

Glucoripper is a personal-use Android app that reads blood-glucose readings
from your Bluetooth glucose meter and writes them to **Google Health Connect**
on your device. This policy describes exactly what data the app handles, where
it goes, and what it does not do.

## Who we are

Glucoripper is developed and maintained by **ChimpSec** (Zachery Frazier,
Louisville, KY, USA). Contact: **admin@chimpsec.com**.

## Data the app processes

Glucoripper processes the following data entirely on your device:

- **Blood-glucose readings** — numeric value, timestamp, meal context, meter
  serial / battery status — pulled from your paired meter over Bluetooth Low
  Energy using the standard Bluetooth Glucose Profile.
- **Bluetooth device identifiers** — the MAC address and display name of the
  meter(s) you have paired, stored locally to enable delta syncing.
- **User preferences** — display unit (mg/dL or mmol/L), target glucose range,
  auto-push mode.
- **Per-reading annotations you add yourself** — optional meal relation
  overrides, "how you felt" tags, and free-text notes.
- **Sync history** — a local log of past sync attempts (timestamp, success
  or failure, number of readings pulled).

## Where the data goes

- **Google Health Connect** — Glucoripper writes `BloodGlucoseRecord` entries
  to Health Connect using the permissions you grant at first launch. Once in
  Health Connect, the data is governed by [Google's Health Connect privacy
  policy](https://support.google.com/android/answer/12201227) and by whichever
  other apps you choose to share it with, via Health Connect.
- **Your device's DataStore** — preferences, annotations, staged-but-not-yet-
  pushed readings, and sync history are stored in app-private files on your
  phone. They never leave the device except through the CSV export below.
- **Your Wear OS watch** (if paired) — when you have the Glucoripper watch
  companion installed, your latest reading and the past 24 hours of readings
  are sent from the phone to the watch via Google's Wearable `DataClient`,
  which moves the payload over the encrypted Bluetooth/Wi-Fi link that Google
  Play Services already maintains between your phone and watch. The watch
  stores a copy in its own app-private DataStore. No server is involved.
- **CSV export (optional, user-initiated)** — when you tap "Export CSV",
  Glucoripper writes a file to the location you select via Android's system
  file picker. Where that file goes after is entirely under your control.

## What Glucoripper does **not** do

- It does **not** transmit your glucose data to any server operated by the
  developer or any third party.
- It does **not** include analytics, advertising, tracking SDKs, or crash
  reporters.
- It does **not** require or create an account.
- It does **not** use your data for profiling or for any purpose beyond
  showing it to you and relaying it to Health Connect / your watch.
- It does **not** back up your data to Google Drive or any other cloud
  (Android auto-backup is disabled in the app manifest).

## Third parties

The only third party in the data flow is **Google**, in their role as the
operator of Health Connect, Google Play Services (which routes the phone↔watch
DataClient messages), and the Play Store (which distributes the app). Their
handling of that data is covered by Google's own privacy policy.

Glucoripper does not share data with any other third party.

## Security

Data stays on your device at rest. Transport to Health Connect and to the
paired watch is handled by system APIs that use Android's standard process
isolation and, in the case of Wearable `DataClient`, encrypted Bluetooth / Wi-Fi
transport between Google-Play-paired devices.

Because everything is local, the security of the data ultimately depends on
the security of the device itself (lock screen, encryption, etc.).

## Your controls

- **Uninstall** Glucoripper to remove all of its app-private data from the
  phone. Uninstall on the watch to remove the watch-side copy. Readings already
  written to Health Connect remain in Health Connect; you can remove them in
  the Health Connect app.
- **Revoke permissions** at any time via Android Settings → Apps →
  Glucoripper → Permissions, or via Health Connect's own app-permissions
  panel.
- **Unpair the meter** inside Glucoripper's "Devices" screen to remove its
  MAC address and sync state from the app's storage.

## Children

Glucoripper is not directed to children under 13 and does not knowingly
collect data from them.

## Changes to this policy

If the policy changes in any meaningful way, the "Last updated" date at the
top will be revised and the new version will be committed to this repository.
The history of changes is visible via git.

## Contact

For questions about this policy: **admin@chimpsec.com**.
