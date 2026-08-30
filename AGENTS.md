# AGENTS.md — Boomeranger

Guidance for humans and coding agents working on this codebase.

## Product summary

Boomeranger creates boomerang-style MP4s from a local video:

1. Pick via SAF
2. Validate / trim to max **3 seconds**
3. Optionally downscale (Original / FHD / HD)
4. Build reverse segment
5. Concatenate `(forward + reverse) × repeatCount` (2–4)
6. Save via MediaStore and preview

## Architecture decisions

| Decision | Choice | Why |
|----------|--------|-----|
| UI | Jetpack Compose + Material 3 | Modern Android UI stack |
| State | MVVM + `StateFlow` in `BoomerangViewModel` | Survives configuration changes; export job lives in `viewModelScope` |
| Trim / scale / concat / export | Media3 Transformer | Strong composition, scaling (`Presentation`), progress, HDR tone-map hooks |
| Reverse generation | Explicit `ReverseVideoBuilder` | Media3 does **not** provide a reverse-frames edit; pretending otherwise would be incorrect |
| Audio | Mute by default | Avoids fake reverse-audio; simplest correct behavior |
| Storage | SAF + MediaStore + FileProvider | Scoped storage; no broad legacy permissions |
| FFmpegKit | **Not used** | Abandoned / risky default for new apps |

## Package map

```
com.boomeranger.app
├── MainActivity.kt
├── BoomerangApplication.kt
├── ui/                 # Compose UI + ViewModel
├── domain/             # BoomerangExportUseCase
├── data/               # VideoMetadataReader, VideoPickerManager, ExportRepository
├── media/              # Reverse + Media3 helpers + encoder
├── model/              # Export settings / stages / metadata
└── util/               # Size resolver, bitrate, URI copy, logging
```

## Generated / key files

### Gradle / manifest
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
- `app/build.gradle.kts` — Compose, Media3 1.5.1, minSdk 26, compile/target 35; versionName/versionCode from Gradle properties (GitHub Releases override from the tag)
- `.github/workflows/android-ci.yml` — PR/push Dev APK (`com.boomeranger.app.debug`)
- `.github/workflows/release.yml` — GitHub Release → signed sideload APK asset
- `app/src/main/AndroidManifest.xml` — single activity + FileProvider

### Models
- `model/VideoMetadata.kt` — includes `MAX_INPUT_DURATION_MS = 3000`
- `model/ExportSettings.kt`, `RepeatCount.kt`, `ResolutionOption.kt`
- `model/ExportStage.kt`, `ExportResult.kt`, `BoomerangUiState.kt`

### Data
- `data/VideoPickerManager.kt` — `GetContent` / `video/*`
- `data/VideoMetadataReader.kt` — `MediaMetadataRetriever`
- `data/ExportRepository.kt` — MediaStore insert + share/view intents

### Media pipeline
- `media/ForwardClipPreparer.kt` — Media3 trim + scale + optional mute
- `media/BitmapFrameExtractor.kt` — oriented frame JPEGs to disk
- `media/YuvConverter.kt` + `FrameSequenceEncoder.kt` — H.264 encode
- `media/ReverseVideoBuilder.kt` — reverse segment assembly
- `media/VideoConcatenationService.kt` — Media3 `(F+R)×N`
- `media/Media3TransformHelper.kt` — Transformer coroutine wrapper, HDR tone-map

### Domain / UI
- `domain/BoomerangExportUseCase.kt` — end-to-end orchestration + progress stages
- `ui/BoomerangViewModel.kt` — UI state machine
- `ui/BoomerangAppScreen.kt` — home, metadata, settings, progress, result
- `ui/BoomerangerSplash.kt` — branded Compose splash (after AndroidX SplashScreen handoff)

## Media pipeline (detailed)

```
SAF Uri
  → copy to cache (UriFileCopier)
  → read metadata (VideoMetadataReader)
  → resolve output size (OutputSizeResolver)
  → Media3: trim ≤3s + scale + mute → forward.mp4
  → extract frames → encode reverse order → reverse.mp4
  → Media3 Composition: (forward + reverse) × N → boomerang_*.mp4
  → MediaStore Movies/Boomeranger
```

### Progress stages
`Reading metadata` → `Preparing forward clip` → `Generating reverse segment` →
`Building boomerang cycles` / `Exporting final video` → `Saving to storage` → `Completed`

### Fidelity policy
- Default keeps oriented source dimensions.
- Aspect ratio always preserved; never upscale.
- Bitrate = max(scaled source bitrate, ~0.2 bits/pixel/frame baseline), clamped.
- Re-encoding is unavoidable for reverse; do not claim bit-exact quality.

### HDR policy
- Reverse path materializes bitmaps → SDR.
- Transformer `Composition.Builder.setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)` so forward/concat match (Media3 1.5 moved HDR mode onto `Composition`).
- If tone-mapping fails on a device, export errors with a useful message.

## Where to change behavior later

| Behavior | Primary file(s) |
|----------|-----------------|
| Max input duration (3s) | `model/VideoMetadata.kt` (`MAX_INPUT_DURATION_MS`) + `ClipWindowResolver` / trim UI |
| Trim window start | `BoomerangUiState.trimStartMs` + `ClipWindowPicker` + `ForwardClipPreparer` |
| Repeat options (2/3/4) | `model/RepeatCount.kt` + UI selector |
| Resolution caps | `model/ResolutionOption.kt` + `util/OutputSizeResolver.kt` |
| Bitrate heuristic | `util/BitrateCalculator.kt` |
| Frame rate options (30/60) | `model/FrameRateOption.kt` + UI selector + `BitmapFrameExtractor` |
| Speed options (1x/2x/4x) | `model/SpeedOption.kt` + encoder/GIF delay multipliers |
| Export format (MP4/GIF) | `model/ExportFormat.kt` + `GifSequenceEncoder.kt` + use case branch |
| Mute default | `model/ExportSettings.kt` |
| Reverse quality (JPEG 95, fps) | `media/BitmapFrameExtractor.kt`, `FrameSequenceEncoder.kt` |
| Concat / Media3 mime / HDR mode | `media/Media3TransformHelper.kt` |
| Gallery path / share | `data/ExportRepository.kt` + `gallery_album` string (debug overlay: `Boomeranger Dev`) |
| Stage labels / UX copy | `model/ExportStage.kt`, `ui/BoomerangAppScreen.kt` |
| GitHub Release APK / versioning | `.github/workflows/release.yml`, `scripts/release-version.sh`, `gradle.properties` (`app.versionName` / `app.versionCode`) |
| Dev vs stable app IDs | `app/build.gradle.kts` (`applicationIdSuffix = ".debug"`), `src/debug/res/values/strings.xml` |

## Testing notes for agents

- Prefer a short (~1–3s) H.264 MP4 on a real device for export validation.
- Emulators may lack hardware encoders; software paths can be slow or fail.
- Config-change test: rotate during export; ViewModel should keep progress.
- Longer-than-3s input should show trim info and still export.
- CI: `.github/workflows/android-ci.yml` builds the Dev APK (`com.boomeranger.app.debug`) and uploads the `boomeranger-dev-apk` artifact. Release APKs stay `com.boomeranger.app`.
- Releases: publishing a GitHub Release tag `vMAJOR.MINOR.PATCH` runs `.github/workflows/release.yml`, which builds a signed sideload APK and attaches `Boomeranger-<version>.apk` to the release. Shared Android setup lives in `.github/actions/setup-android`. Tag → version mapping is `scripts/release-version.sh`.

## Non-goals / avoided approaches

- FFmpegKit as the primary pipeline
- Flutter / React Native
- Giant Activity with media code inline
- Claiming Media3 can reverse frames with a one-liner
- Fake reverse-audio
