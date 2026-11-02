# Fork notes (lynxunov/android-voice-input)

Based on [futo-org/voice-input](https://github.com/futo-org/voice-input). This file lists every
intentional difference so that syncing with upstream stays mechanical:
`git fetch upstream && git merge upstream/master` — conflicts can only be in the places below.

## Goals
Private, offline, on-device. Single flavor (`fDroid`). No telemetry, no payments, no update checker,
no network permission, no Google Play integration, no model downloader.

## Differences from upstream

**Build**
- `app/build.gradle`: `applicationId com.lynxunov.voiceinput`, fixed `versionCode`/`versionName`,
  only the `fDroid` flavor (no per-flavor source sets), no OkHttp / ACRA / Play Billing dependencies,
  no crash-report properties.
- `shared/build.gradle`: the `downloadModel` task (fetches a model from the network at build time and
  bundles it in the APK) and its checksum helpers are removed. No model is bundled.

**Manifest**: removed `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`;
removed the download, payment, billing and update-checking components; application class is
`LynxVoiceInputApplication`; added `models.ModelPickerActivity`.

**Removed directories/files**: `app/src/{dev,nonDev,withUpdateChecking,withoutUpdateChecking,playStoreBilling,payPalBilling}`,
`payments/`, `downloader/`, `settings/Payment*.kt`, `settings/pages/Payment.kt`, `CrashLoggingApplication.kt`.
All call sites (notices, update checks, payment routes, dev settings, "what's new") were removed from
`RecognizeActivity`, `VoiceInputMethodService`, `SettingsActivity`, `SettingsUtils`, `Settings`, `Home`,
`Advanced`, `InputTest`, `Credits`, `Help`.

**Added**
- `LynxVoiceInputApplication.kt`: loads `libvoiceinput.so` and owns the shared `ModelManager`
  (the two useful things upstream's `CrashLoggingApplication` did), without any crash reporting.
- `models/`: Model Manager. The user picks each model file with the system file picker; the file is
  used in place (persisted SAF permission, loaded through `/proc/self/fd`, never copied; a temporary
  copy is only made as a fallback if a provider refuses direct access).
  `SafBackedModel` wraps upstream's `ModelLoader`s, so all upstream screens and the recognizer work
  unchanged. Hook point: `loadRecognizerSettings()` in `Util.kt` (`withSafStorage`).
- `strings-lynx.xml`: strings for the Model Manager (separate file to keep `strings.xml` merge-clean).

**Small edits to upstream files**
- `Util.kt`: `startModelDownloadActivity` opens the Model Manager; `withSafStorage` in
  `loadRecognizerSettings`; removed the legacy-model migration helper.
- `shared/.../Models.kt`: asr4all-m is a normal selectable model instead of a bundled asset
  (`DEFAULT_ENGLISH_MODEL_IDX` adjusted accordingly).
- `shared/.../AudioRecognizer.kt`: `openPermissionSettings` opens this app instead of an activity
  that only exists inside FUTO Keyboard.
- `settings/pages/Models.kt`: permanent link to the Model Manager.

## Syncing with upstream
```
git fetch upstream
git merge upstream/master
git submodule update --init --recursive
```
