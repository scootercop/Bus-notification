# Bus Notifier (Android)

An Android app that polls Auckland Transport's realtime GTFS feed on the
device, during user-defined windows, and fires a local push notification when
a watched bus is within N minutes of a watched stop.

Originally a Python script triggered by GitHub Actions cron, rebuilt as a
standalone Android app so triggering is reliable (no shared-runner queue
delays) and configurable from the phone.

## What it does

- Tracks any number of independent "watches" — each is a `(bus number, stop
  code, active days, time window, ETA threshold, poll interval)` tuple.
- During an active window, wakes up every `pollIntervalMin` minutes via
  `AlarmManager.setExactAndAllowWhileIdle`, calls the AT realtime API in a
  one-shot `CoroutineWorker`, and notifies if the soonest ETA is at or below
  the threshold.
- De-dupes by `trip_id` so the same bus doesn't notify twice.
- Survives reboot via a `RECEIVE_BOOT_COMPLETED` receiver.

## Stack

- Kotlin · Jetpack Compose (Material 3)
- Room · WorkManager · AlarmManager
- OkHttp · kotlinx.serialization
- `androidx.security.crypto` `EncryptedSharedPreferences` for the API key

## Quick start

1. Sign up at <https://dev-portal.at.govt.nz>, subscribe to the **Public
   Restricted API**, copy your primary key.
2. Open Android Studio, **File → Open** the `BusNotifier/` directory, let
   Gradle sync (JDK 17 required).
3. `./gradlew :app:assembleDebug` to build, or run from the IDE.
4. On first launch: paste your API key in **Settings**, accept the
   notifications prompt, and grant "ignore battery optimisation" if you want
   the alarms not to be deferred.
5. Tap **+ Add watch** — e.g. bus `712`, stop `6087`, Mon–Fri, `07:20–08:00`,
   notify ≤ `6` min, poll every `5` min.
6. **Test now** runs an immediate one-shot check so you can confirm the API
   call works before relying on the schedule.

## Project layout

```
BusNotifier/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/navalrishi/busnotifier/
│   │   ├── BusNotifierApp.kt          # Application class, channel setup
│   │   ├── MainActivity.kt            # Compose NavHost
│   │   ├── data/                      # Room (Watch, NotifiedTrip) + KeyStore
│   │   ├── network/                   # AtClient (OkHttp + kx.serialization)
│   │   ├── domain/                    # EtaCalculator, Notifier, WatchScheduler
│   │   ├── background/                # PollAlarmReceiver, BusCheckWorker, BootReceiver
│   │   └── ui/                        # WatchListScreen, WatchEditScreen, SettingsScreen, Theme
│   └── res/                           # icons, strings, themes, backup rules
└── screenshots/                       # Captured during emulator smoke-test
```

## Reliability notes

- Aggressive OEM battery managers (Xiaomi, Oppo, OnePlus) can still kill
  exact alarms. The app links you to the system **Battery optimisation** and
  **Allow exact alarms** settings; on those OEMs you may also need vendor-
  specific opt-outs.
- AT realtime data is best-effort. Trips appearing in the GTFS feed *can*
  later disappear; the worker treats per-trip stoptime errors as
  skip-and-continue, not fail-the-run.

## License

MIT — do what you want with it.
