# 🎧 AuraSurround — LSPosed 8D & Surround Audio Module

> System-level 8D, Surround, Concert Hall, Stadium, Cave & Spatial audio for **every app** on your rooted Android device.

[![Build APK](https://github.com/YOUR_USERNAME/AuraSurround/actions/workflows/build.yml/badge.svg)](https://github.com/YOUR_USERNAME/AuraSurround/actions/workflows/build.yml)

---

## ✨ Features

| Feature | Description |
|---|---|
| 🎧 **8D Audio** | Sound rotates 360° around your head in real-time |
| 🎼 **Concert Hall** | Large hall reverb, like a live performance |
| 🏟️ **Stadium** | Massive room simulation |
| 🗿 **Cave Echo** | Deep, immersive cave reverb |
| 🔊 **Headphone Spatial** | Optimized virtual surround for earphones |
| ⚙️ **Custom Mode** | Full manual control over all parameters |
| 🔈 **Bass Boost** | Deep bass enhancement |
| 🌀 **Surround Depth** | Virtualizer strength control |
| ↻ **8D Speed** | Adjust rotation speed (0.1x–2.0x) |
| 📱 **Works on ALL apps** | Spotify, YouTube Music, VLC, WhatsApp, etc. |

---

## 📋 Requirements

- ✅ Rooted Android device (Android 10+)
- ✅ **Magisk** installed
- ✅ **LSPosed** framework installed (Zygisk or Riru version)
- ✅ CMF Phone 1 / NOS 4.0 ✓ tested target

---

## 🚀 Installation

1. Download the APK from [Releases](https://github.com/YOUR_USERNAME/AuraSurround/releases) or build from source
2. Install APK normally
3. Open **LSPosed Manager** → Modules
4. Enable **AuraSurround**
5. Select scope: check **System Framework** (`android`) — this applies to ALL apps
6. **Reboot** your device
7. Open AuraSurround app, configure your effect, hit **Save & Apply**
8. Play music! 🎶

---

## 🔧 How It Works

```
AudioTrack.play()  ←  hooked by LSPosed
       ↓
 AudioSession ID captured
       ↓
 Effect Chain applied:
   ├── Virtualizer  (surround depth)
   ├── PresetReverb (room/space simulation)
   ├── BassBoost    (low frequency enhancement)
   ├── Equalizer    (frequency shaping)
   └── AudioPanner  (8D stereo rotation thread — only in 8D mode)
```

The hook intercepts every `AudioTrack.play()` call system-wide. Android's built-in `android.media.audiofx` API handles the actual DSP — this is the same API used by Poweramp, Dolby, and other premium equalizer apps, just applied at a lower level.

---

## 🏗️ Build from Source

```bash
git clone https://github.com/YOUR_USERNAME/AuraSurround
cd AuraSurround
chmod +x gradlew
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

Or push to GitHub and let **GitHub Actions** build it automatically!

---

## 📂 Project Structure

```
AuraSurround/
├── app/src/main/
│   ├── java/com/demon/aurasurround/
│   │   ├── hook/
│   │   │   ├── MainHook.kt        ← LSPosed entry, AudioTrack hooks
│   │   │   └── AudioPanner.kt     ← 8D rotation thread
│   │   ├── model/
│   │   │   └── AudioEffectPrefs.kt ← Settings data model + presets
│   │   ├── ui/
│   │   │   └── MainActivity.kt    ← Beautiful dark UI
│   │   ├── viewmodel/
│   │   │   └── MainViewModel.kt   ← ViewModel
│   │   └── utils/
│   │       └── PrefUtils.kt       ← Cross-process prefs sharing
│   ├── res/
│   │   └── ...                    ← Layouts, drawables, themes
│   └── assets/
│       └── xposed_init            ← LSPosed entry point declaration
└── .github/workflows/
    └── build.yml                  ← Auto-build on push
```

---

## ⚙️ Effect Modes

| Mode | Virtualizer | Reverb | Bass | 8D Panning |
|------|-------------|--------|------|------------|
| 8D Surround | 100% | Small Room | 30% | ✅ |
| Concert Hall | 70% | Large Hall | 20% | ❌ |
| Stadium | 90% | Large Room | 50% | ❌ |
| Cave Echo | 60% | Plate | 10% | ❌ |
| Headphone Spatial | 85% | Medium Hall | 40% | ❌ |
| Custom | Manual | Manual | Manual | ❌ |

---

## ❗ Troubleshooting

**Module not active?**
→ Enable in LSPosed → check `android` in scope → reboot

**No effect on Spotify/YouTube?**
→ Some apps use their own audio pipeline. Enable scope for that specific app package in LSPosed too.

**Audio crackling?**
→ Reduce Virtualizer Depth or switch off BassBoost

**8D effect too intense?**
→ Reduce rotation speed to 0.2x–0.3x

---

## 📄 License

MIT License — made with 💜 by Demon
