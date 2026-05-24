# supertonic-kmp

**Kotlin Multiplatform library for on-device, offline TTS — Android & iOS.**

Built on top of [Supertonic v3](https://github.com/supertone-inc/supertonic) by [Supertone Inc.](https://supertone.ai) (Korea) — a neural speech synthesis engine that runs fully offline via ONNX Runtime. 31 languages including Russian, Ukrainian, Polish, and more.

---

## Why this library exists

[Supertonic v3](https://github.com/supertone-inc/supertonic) was released by Supertone in April 2026 and brought multilingual support to their already impressive neural TTS engine. No clean Kotlin Multiplatform wrapper existed for it.

The only existing KMP option — [CopiloTTS](https://github.com/sigmadeltasoftware/CopiloTTS) — targets Supertonic v2 (English only) and is effectively unmaintained.

This library fills that gap: a minimal, idiomatic KMP wrapper that exposes Supertonic v3 to Android and iOS projects without forcing any playback architecture on you.

**Core principle:** the library only **generates WAV bytes**. Playback is your responsibility. No forced queues, players, or abstractions. Want `speak()`? It's there as a convenience. Want to plug into your own prefetch pipeline? Use `generate()` or `generateChunked()` and do whatever you want with the bytes.

---

## Features

- 31 languages out of the box — `ar` `bg` `hr` `cs` `da` `nl` `en` `et` `fi` `fr` `de` `el` `hi` `hu` `id` `it` `ja` `ko` `lv` `lt` `pl` `pt` `ro` **`ru`** `sk` `sl` `es` `sv` `tr` `uk` `vi`
- 10 bundled voice presets (M1–M5, F1–F5) — no separate download needed
- One-time model download (~280 MB), then fully offline
- Resume-friendly download with real progress
- `generateChunked()` — streaming output for long texts, first chunk arrives fast
- 44100 Hz, 16-bit PCM mono output — standard WAV, plays anywhere
- No espeak, no native toolchain dependencies on Android

---

## Quick start

```kotlin
// 1. Initialize
val tts = SupertonicTts(
    SupertonicConfig(
        storageDir = context.filesDir.path + "/supertonic", // Android
        // storageDir = NSDocumentDirectory + "/supertonic"  // iOS
        defaultLang = "ru",
        defaultVoice = SupertonicVoice.M1,
    )
)

// 2. Download models (~280 MB, once)
tts.download().collect { state ->
    when (state) {
        is DownloadState.Downloading -> showProgress(state.fraction)
        is DownloadState.Ready       -> enableSpeakButton()
        is DownloadState.Error       -> showError(state.cause.message)
        else -> {}
    }
}

// 3a — Generate WAV, play with your own player
val result = tts.generate("Hello, world!", lang = "en", voice = SupertonicVoice.F1)
if (result is GenerateResult.Success) {
    myPlayer.play(result.wav) // 44100 Hz, 16-bit PCM, mono
}

// 3b — Stream long text chunk by chunk
tts.generateChunked("A long article with many sentences...")
    .collect { result ->
        if (result is GenerateResult.Success) {
            myQueue.enqueue(result.wav) // play first chunk while next is generating
        }
    }

// 3c — Let the library play for you (convenience only)
tts.speak("Hello, world!")

// 4. Free resources
tts.close()
```

---

## Voices

10 voice presets are bundled inside the library — no download required:

| Voice | Gender |
|-------|--------|
| M1, M2, M3, M4, M5 | Male |
| F1, F2, F3, F4, F5 | Female |

---

## Configuration

```kotlin
SupertonicConfig(
    storageDir     = "...",              // you provide the path, library doesn't assume anything
    inferenceSteps = 8,                  // 2–4 fast, 8 balanced, 12+ max quality
    defaultLang    = "ru",
    defaultVoice   = SupertonicVoice.M1,
    defaultSpeed   = 1.0f,               // 0.7–2.0
    huggingFaceBaseUrl = "...",          // optional: replace with your own CDN
)
```

Don't know the language of the input text? Pass `lang = "na"` and Supertonic will detect it automatically.

---

## iOS integration

### 1. Add ONNX Runtime via Swift Package Manager

In Xcode: **File → Add Package Dependencies**
- **URL**: `https://github.com/microsoft/onnxruntime-swift-package-manager`
- **Version**: `1.20.0+`
- **Product**: `OnnxRuntimeBindings`

### 2. Add SupertonicBridge.swift to your Xcode project

Copy `SupertonicBridge.swift` from this repo into your Xcode project and initialize it in your app entry point:

```swift
import SupertonicKMP  // your KMP framework name

@main
struct iOSApp: App {
    init() {
        SupertonicHolder.shared.bridge = SupertonicBridge()
    }
    // ...
}
```

### 3. Copy bundled voice styles into the app bundle

Add a **Run Script Phase** in Xcode (after the Kotlin framework build phase):

```bash
RESOURCES_SRC="$SRCROOT/../core/build/bin/iosArm64/debugFramework/SupertonicKMP.framework/supertonic"
APP_PATH="$BUILT_PRODUCTS_DIR/$WRAPPER_NAME"

if [ -d "$RESOURCES_SRC" ] && [ -d "$APP_PATH" ]; then
    cp -R "$RESOURCES_SRC" "$APP_PATH/"
    echo "Copied to app bundle!"
else
    echo "Skipped"
fi
```

> Adjust the path to `core/build/bin/...` to match your module name and build configuration.

---

## How it works

This library is a KMP wrapper around the [Supertonic v3](https://github.com/supertone-inc/supertonic) engine by [Supertone Inc.](https://supertone.ai) The underlying inference runs four ONNX models in sequence:

```
text
  │
  ▼  preprocessText("<ru>Hello!</ru>")   ← NFKD normalization + language tokens
  │  tokenize() → int64 token ids        ← unicode_indexer.json lookup
  │
  ├─► duration_predictor.onnx
  │     inputs:  text_ids, style_dp, text_mask
  │     output:  duration in seconds → latent length
  │
  ├─► text_encoder.onnx
  │     inputs:  text_ids, style_ttl, text_mask
  │     output:  text_emb [1, 256, text_len]
  │
  ├─► vector_estimator.onnx  ×N steps
  │     inputs:  noisy_latent, text_emb, style_ttl,
  │              latent_mask, text_mask, current_step, total_step
  │     output:  denoised_latent (direct replacement each step)
  │     method:  flow matching — Gaussian noise → speech latent
  │
  └─► vocoder.onnx
        input:   latent [1, 144, latent_len]
        output:  wav_tts [1, num_samples]
          │
          ▼
        WavBuilder → WAV ByteArray (44100 Hz, 16-bit PCM, mono)
```

Voice styles (`style_ttl` and `style_dp`) are bundled inside the library as JSON files — no extra download needed.

---

## Known issues

### Conflict with sherpa-onnx

If your app uses both `supertonic-kmp` and `sherpa-onnx`, you will hit a build error:

```
2 files found with path 'lib/arm64-v8a/libonnxruntime.so'
```

Both libraries bundle their own copy of `libonnxruntime.so`. Fix it by adding `packagingOptions` to your app's `build.gradle.kts`:

```kotlin
android {
    packaging {
        jniLibs {
            pickFirst("lib/arm64-v8a/libonnxruntime.so")
            pickFirst("lib/x86_64/libonnxruntime.so")
            pickFirst("lib/armeabi-v7a/libonnxruntime.so")
        }
    }
}
```

Tested with `sherpa-onnx 1.12.34` and `onnxruntime-android 1.26.0`.

### iOS resource copying

Voice style JSON files are bundled in the KMP framework but not automatically embedded in the app bundle by Xcode. The Run Script Phase described above is required. This is a known limitation of KMP resource handling on iOS.

### Android Studio shows SPM warnings

Android Studio may show `Missing package product 'onnxruntime'` warnings because it sees the Xcode project's SPM references. These are harmless — Android builds work correctly. iOS must be built from Xcode.

---

## Dependencies

**Android** (`androidMain`):
```kotlin
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.26.0")
```

**iOS** — via Swift Package Manager in Xcode:
```
https://github.com/microsoft/onnxruntime-swift-package-manager @ 1.20.0+
Product: OnnxRuntimeBindings
```

**Common**:
```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
```

---

## License

Library code — **MIT**

The Supertonic v3 models and engine are developed and owned by [Supertone Inc.](https://supertone.ai) and distributed under the **OpenRAIL-M** license (commercial use permitted). This library is an independent community wrapper and is not affiliated with or endorsed by Supertone.

Model details: [Supertone/supertonic-3 on HuggingFace](https://huggingface.co/Supertone/supertonic-3)  
Engine source: [supertone-inc/supertonic on GitHub](https://github.com/supertone-inc/supertonic)