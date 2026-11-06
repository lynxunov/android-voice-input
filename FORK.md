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
- `Util.kt`: `startModelDownloadActivity` opens the Model Manager instead of upstream's downloader;
  `loadRecognizerSettings()` resolves every model through `withSafStorage` right after upstream's own
  `MODEL_OPTIONS` resolution, so every screen, the recognizer and the IME see the same SAF-backed models; `withSafStorage` in
  `loadRecognizerSettings`; removed the legacy-model migration helper.
- `shared/.../Models.kt`: asr4all-m is a `ModelDownloadable` with our checksum instead of a
  `ModelBuiltInAsset` (no model is bundled in this fork). Everything else (`ALL_MODELS`, `MODELS_BY_ID`,
  `lookupModel`, the per-language `MODEL_OPTIONS` system) is upstream's.
- `shared/.../AudioRecognizer.kt`: `openPermissionSettings` opens this app instead of an activity
  that only exists inside FUTO Keyboard.
- `settings/pages/Models.kt`: taken from upstream as-is (its per-language model pickers), plus one added
  row linking to the Model Manager. The "Model" subtitle on the Home screen (same-named function in this
  file) only lists models that actually have a file.
- `models/ModelPickerActivity.kt`: `selectIfCurrentIsMissing` rewritten against upstream's `MODEL_OPTIONS`
  string-set format (`"lang:modelId"` / `"multi:modelId"`) instead of the old fixed-index lists; model
  groupings for the picker UI now come from `ALL_MODELS` (upstream removed the old `ENGLISH_MODELS` /
  `MULTILINGUAL_MODELS` lists).
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

- **unreleased** – Merged upstream's model-options rewrite (per-language model selection replacing fixed
  English/multilingual indices; `MODEL_OPTIONS`, `ALL_MODELS`, `lookupModel`, migration from the old
  settings). Re-applied on top: SAF model resolution, the Model Manager nav entry and its model groupings,
  `startModelDownloadActivity`. No behavior change intended beyond upstream's own new per-language picker UI.


## 1.4.3-lynx (unreleased) – build fix + review fixes
- Fix: `SafBackedModel` implements upstream's new abstract `id`/`legacyId` (build error).
- Fix: `Util.loadRecognizerSettings` – removed same-scope `modelOptions` redeclaration; empty relevant-model list no longer crashes (falls back to first known model → "pick a file").
- Fix: `AudioRecognizer` recreates stop signal + audio channel per recording (reused recognizer ended 2nd session immediately).
- Fix: IME resets composing/pending text per input session, calls `super.onFinishInputView`, recovery check `>= 0`.
- Fix: Model Manager auto-assign no longer overwrites intentional empty (`lang:`) slots; English-only models no longer fill the multi slot.
- Fix: `settings/pages/Models.kt` had a stray `import` above `package` (syntax error → cascading unresolved `ModelsScreen`/`modelsSubtitle`); `Home.kt` dropped unused `ENABLE_MULTILINGUAL` (removed upstream in v2).
- Fix: Model Manager auto-select now also fills the English slot when an English-only model is picked (empty `en:` = "use multilingual" is replaced; multilingual picks still never overwrite empty overrides).

## 1.5.0-lynx – mixed-language dictation, phase 1
- New: `shared/.../whisper/PauseSegmenter.kt` – energy-based pause detector (350 ms gap, ≥0.8 s speech per chunk, 25 s max; adaptive level).
- New: `MultiModelRunner` segmented path: each chunk is language-detected (among enabled languages) and transcribed on the multilingual model; no whole-recording re-run, no model swap. Short chunks also make the encoder cheaper (engine scales audio context with length). A recording with only one chunk uses the classic path (per-language model if configured).
- Only for non-streaming models with language detection (Whisper); streaming models keep upstream behaviour.
- Advanced Settings: "Multiple languages in one recording" (default on), "Per-language models in mixed recordings" (default off).
- Limits: a single foreign word without pauses around it stays in the surrounding chunk's language. Not yet done: primary language pair, show detected languages (needs native change), dictionaries, pause/resume, spoken punctuation.

## 1.5.1-lynx – speed (native Release), import fixes, pause options, progress
- Perf (likely main cause of 20–50x slowness vs. upstream, unconfirmed until measured): `CMakeLists.txt` forces `CMAKE_BUILD_TYPE=Release` so the native engine (ggml) is optimised in debug APKs too (was -O0). First build after this change recompiles the native part (slow once).
- Fix: importing a file that is already assigned to another model is refused; a file that is the official file of a *different* model is refused before anything is stored (hash checked first, reused for verify).
- Fix: Remove / Replace / Delete copy only release the Android file permission when no other model uses the same file (removing a wrongly imported model no longer breaks the correct one).
- Fix: "Delete copy" now also unassigns the model (same as Remove).
- New: Input → "Cut long pauses" (default on; inactive while Auto-stop on silence is on): single-language recordings with non-streaming models are stripped of long silences before processing.
- New: Advanced → pause length slider (200–1500 ms, default 350; inactive while Auto-stop is on). Mixed-language chunking uses it too.
- New: "Processing part k of n" progress for chunked recordings (producer/consumer, total known right after stop).
- Not yet: separate language-ID model, merging same-language chunks, parallel chunk transcription, show detected languages.

## 1.5.2-lynx – readiness, UI glitches, noise
- Fix: models are loaded *before* the microphone opens and the start sound plays (first recording after app start no longer records/beeps during loading).
- Fix: "Processing…" no longer covers the recording screen while chunks are processed in the background (status/progress are only shown after recording stopped).
- Fix: progress text ("Processing part k of n…") was overwritten by "Processing…" immediately; now set after the status.
- Fix: the model summary under "Model" on the main settings page refreshes after imports/removals and selection changes.
- Fix: chunks that are only background noise (loudest moment < 20 % of the loudest speech so far) are dropped, which prevents Whisper hallucinating sentences after speech (e.g. paper rustling).
- Open: first chunk sometimes duplicated / detected in the wrong language (not reproducible yet; short first chunks are the prime suspect).

## 1.5.3-lynx – sorting, speech check, context, thread safety
- Fix: Model Manager groups models by family (Whisper, asr4all, …), then by size.
- Replace the loudness-based noise filter by a speech check (WebRTC VAD, share of speech frames; `SpeechCheck.kt`). Loud surroundings no longer suppress speech. If every chunk of a recording is rejected, the longest is still transcribed. Setting: Advanced → "Ignore sounds that are not speech" (default on).
- New: each chunk is transcribed with the previous text (last 200 chars) as context. Setting: Advanced → "Use earlier text as context" (default on).
- New: exact "A. A." duplicate output of one chunk is collapsed to one copy (Whisper artefact when audio starts abruptly).
- Perf/robustness: `ModelManager` is thread-safe; recorder setup runs off the UI thread (first-start freeze under investigation).
- Design notes: true same-language merging needs a detection-only pass per chunk (the engine does not report the detected language), i.e. ~2x encoder work and delayed transcription of the last run; context carry-over was chosen first as it gives most of the accuracy gain at no latency cost.

## 1.6.0-lynx – pause/resume, primary pair, detected languages, native fixes
- New: pause/resume button on the recording screen (bottom left). Pausing turns the microphone off, closes the current chunk and keeps processing everything said so far in the background; resuming reuses the open recorder (no start-up delay). Pause marker = empty sample array, understood by `PauseSegmenter`; filtered out for the classic and streaming paths.
- New: Advanced → "Primary language pair" (exactly two of the enabled languages): mixed-language chunks are detected between these two only; other enabled languages still work for single-language recordings.
- New: Advanced → "Show detected languages": after each chunk the engine's detected language is shown (JNI `detectedLanguageNative` → `transcribe_detected_language`). Depends on the engine reporting a language after a normal run; shows nothing otherwise.
- New: spinner on the loading screen is driven by frame time (works with system animation scale 0 / power saving), theme-coloured.
- Native (`voiceinput.cpp`), from the private Devin audit (verified in code): `GetFloatArrayElements` was never released in `infer` and `feedStream` (leak per chunk) → RAII guard; `prompt_str` out-of-bounds read; VLA in `jstring2string` → heap; null-check in `cancel`; local references released in `string2jstring`.
- Native: prompt may carry earlier text after `\x01`; it is appended as plain context after the glossary instead of being formatted as glossary words.
- Fix: `RecognizeActivity.onDestroy` null-safe; `visibleToInstantApps` removed from `RecognizeActivity`.
- Reviewed, not changed: `DummyService` returns a null binder (harmless); fd kept open per loaded SAF model (bounded, needed while the engine may read lazily); `is_ahead` and thread-count logic are upstream.
- Decided against (for now): separate language-ID model – a second inference runtime + download, and Whisper would still need to transcribe each chunk; language-batched transcription needs pre-detection per chunk.
