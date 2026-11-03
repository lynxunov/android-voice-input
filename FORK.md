# Fork notes – Lynx Voice Input (lynxunov/android-voice-input)

Based on [futo-org/voice-input](https://github.com/futo-org/voice-input). This file lists every
intentional difference so that syncing with upstream stays mechanical:
`git fetch upstream && git merge upstream/master` — conflicts can only be in the places below.

> **Maintenance rule (requested by the maintainer): every change to this fork must update this file in the
> same commit – the differences list below and the change log at the end. Whoever (or whatever) changes
> the code is responsible for it.**

## License
Same terms as upstream: [FUTO Source First License 1.1](LICENSE.md) – non-commercial use only, no removal of
the Licensor's notices, and a prominent notice that this copy is modified (this file, the README banner and the
in-app *Credits* screen). The app name is **Lynx Voice Input**; it deliberately does not use FUTO's name.
This is a personal-use fork.

## Goals
Private, offline, on-device. Single flavor (`fDroid`). No telemetry, no in-app billing, no update checker,
no network permission, no Google Play integration, no model downloader. The only payment-related thing kept is
a *link* to FUTO's payment page that opens in the external browser (the license forbids removing payment
functionality from distributed copies).

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
All call sites (unpaid notices, update checks, billing, dev settings, "what's new") were removed from
`RecognizeActivity`, `VoiceInputMethodService`, `SettingsActivity`, `SettingsUtils`, `Settings`, `Home`,
`Advanced`, `InputTest`, `Credits`, `Help`.

**Added**
- `LynxVoiceInputApplication.kt`: replaces upstream's `CrashLoggingApplication` minus crash reporting.
  It must keep doing the three useful things that class did: load `libvoiceinput.so`, own the shared
  `ModelManager`, and call `DataStoreHelper.init()` (without it every blocking `getSetting()` silently
  returns its default).
- `models/`: Model Manager. The user picks each model file with the system file picker (persisted SAF
  permission). `SafBackedModel` wraps upstream's `ModelLoader`s, so all upstream screens and the recognizer
  work unchanged. Hook point: `loadRecognizerSettings()` in `Util.kt` (`withSafStorage`).
  Loading: the engine opens models by *path*, so the picked file is handed over as `/proc/self/fd/N`
  (zero-copy) after probing that Android lets the app re-open it that way. If not (scoped storage / some
  providers), a private copy is made **once** in `filesDir/selected-models`, reused afterwards, shown in the
  Model Manager and deletable there. Every load attempt records a diagnostic (shown in the Model Manager).
  Model Manager extras: per-model download link, SHA-256 verification (automatic once after choosing a file,
  plus a manual button; informational only), "Test load" button, file-name mismatch warning.
- `settings/pages/PaymentLink.kt`: "Support FUTO" screen (Home > About) with a button that opens FUTO's payment
  page in the browser. Replaces upstream's billing screens/notices.
- `strings-lynx.xml`: strings for the Model Manager (separate file to keep `strings.xml` merge-clean).

**Small edits to upstream files**
- `Util.kt`: `startModelDownloadActivity` opens the Model Manager; `withSafStorage` in
  `loadRecognizerSettings`; removed the legacy-model migration helper.
- `shared/.../Models.kt`: asr4all-m is a normal selectable model instead of a bundled asset
  (`DEFAULT_ENGLISH_MODEL_IDX` adjusted accordingly).
- `shared/.../AudioRecognizer.kt`: `openPermissionSettings` opens this app instead of an activity
  that only exists inside FUTO Keyboard.
- `settings/pages/Models.kt`: permanent link to the Model Manager; the subtitle only lists models that have a file.
- `strings.xml` (English): app name and in-text product name -> "Lynx Voice Input", streaming tier "mid (default)",
  "Model file required" (was "download required"), feedback text points to this fork. `shared/.../strings-voiceinput.xml`:
  "(Default)" removed from the asr4all M-56 name (it only meant "bundled").
- `Home.kt` / `Help.kt`: the e-mail feedback button (FUTO's support address) is removed; the issue-tracker link
  points to this fork. `Credits.kt`: modification notice at the top.

## Known upstream issues (not fixed here)
- Some themes (e.g. "AMOLED Dark Purple") do not apply correctly – also present in FUTO's own release.

## Syncing with upstream
```
git fetch upstream
git merge upstream/master
git submodule update --init --recursive
```

## Change log
- **1.4.2-lynx (36)** – Model loading rewritten: path-reopen probe, private-copy fallback, per-model diagnostics
  ("Test load", last failure shown). Automatic one-time checksum verification after choosing a file.
  Payment link restored ("Support FUTO"). `FORK.md` maintenance rule added.
- **1.4.1-lynx (35)** – Fix: `DataStoreHelper.init()` was missing, so blocking settings reads returned defaults
  (picked file never seen). Renamed to *Lynx Voice Input*; modification notice (README, Credits); feedback
  points to this fork; subtitle under "Model" only lists models with a file; merged upstream v2.0.1.
- **1.4.0-lynx (34)** – Merged upstream v2 (new `transcribe.cpp` engine, `shared/` module, streaming, new models,
  AGP 9 / Gradle 9 toolchain) and re-applied the fork patchset on top.
- **1.3.7-lynx (32)** – First fork build: single flavor, new application id, no `INTERNET`, payments/updater/ACRA/
  downloader removed, file-picker Model Manager.
