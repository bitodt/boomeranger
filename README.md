# Boomeranger

Native Android app (Kotlin + Jetpack Compose) that turns a short local video into a boomerang-style MP4: forward, reverse, then repeat.

## Requirements

- Android Studio Ladybug / Narwhal (or newer) with Android SDK 35
- JDK 17+
- A physical device or emulator (API 26+)

## Open & run

1. Open this repository root in Android Studio.
2. Let Gradle sync (wrapper uses Gradle 8.11.1).
3. Run the `app` configuration on a device/emulator.

Command line:

```bash
# Ensure local.properties contains sdk.dir=/path/to/Android/Sdk
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

## CI

GitHub Actions (`.github/workflows/android-ci.yml`) runs unit tests, builds the debug APK on pushes/PRs, and uploads `boomeranger-debug-apk` as a workflow artifact (30-day retention).

## How to use

1. Tap **Choose video** and pick a local MP4 (or other Android-readable video).
2. Review thumbnail, filename, duration, resolution, and frame rate.
3. Choose format (`Video` / `GIF`), repeat count (`2` / `3` / `4`), speed (`1x` / `2x` / `4x`), frame rate (`30` / `60` for video; GIF is locked to 30), and resolution (`Original` / `FHD max` / `HD max`).
4. Keep **Mute exported audio** on for video unless you intentionally want forward audio retained (GIF is always silent).
5. Tap **Export boomerang** / **Export GIF**, watch stage progress, then preview / save / share.

Videos longer than 3 seconds are accepted but trimmed to the **first 3 seconds** with an on-screen notice.

## Resolution rules

| Option | Behavior |
|--------|----------|
| Original | Keep oriented source width/height (even dimensions for encoder safety) |
| FHD max | Scale down only if source exceeds an orientation-aware 1920×1080 box (portrait uses 1080×1920); never upscale |
| HD max | Scale down only if source exceeds an orientation-aware 1280×720 box (portrait uses 720×1280); never upscale |

## Architecture (short)

- `ui/` — Compose screens + `BoomerangViewModel` (`StateFlow`)
- `domain/` — `BoomerangExportUseCase` orchestration
- `data/` — picker helpers, metadata reader, MediaStore export
- `media/` — Media3 Transformer prep/concat + explicit reverse-frame encoder
- `model/` / `util/` — settings, sizes, bitrate heuristics

See [AGENTS.md](AGENTS.md) for pipeline details and extension points.

## Known limitations

- **Reverse video is re-encoded.** Media3 has no trivial reverse-edit API; frames are extracted and encoded last-to-first.
- **Not bit-exact.** Output preserves resolution by default and uses a high-quality bitrate heuristic; codec/GOP/bitrate are not cloned bit-for-bit.
- **HDR → SDR.** Bitmap frame extraction and the reverse path are SDR. Media3 forward/concat uses tone-map-to-SDR so color spaces stay consistent. Devices that cannot tone-map fail with a clear error.
- **Audio.** Default export is muted. The app does not synthesize reverse audio.
- **Memory / time.** Reverse generation for 3s high-resolution clips is CPU and I/O heavy (JPEG frame cache + H.264 encode).
- **Input focus.** Common MP4/H.264 inputs are the primary supported path.

## Permissions

Uses the Storage Access Framework for picking and MediaStore for saving. No broad storage permission is requested on modern Android.
