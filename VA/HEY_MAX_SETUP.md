# Hey Jarvis offline wake-word setup

The Android foreground microphone service uses the open-source openWakeWord Android Kotlin library and its pretrained **Hey Jarvis** classifier. Wake-word inference runs on the phone and does not require a Picovoice account, AccessKey, or paid API.

## 1. Model setup

No manual model download or API key is required. The Gradle build downloads these public openWakeWord v0.5.1 ONNX assets into the generated assets directory and packages them into the app:

- `hey_jarvis_v0.1.onnx` — the Hey Jarvis wake-word classifier
- `melspectrogram.onnx` — audio feature extraction
- `embedding_model.onnx` — speech embedding model

The first build needs internet access to fetch the files. Keep the generated files under `VA/app/build/generated/openWakeWordAssets` if you want to rebuild without downloading them again. A clean build may download them again. No credential or extra local.properties entry is needed for wake-word detection.

The Android library is published as `xyz.rementia:openwakeword:0.1.5` and is licensed Apache-2.0. The upstream openWakeWord pretrained models have separate model licensing/attribution terms (CC BY-NC-SA 4.0 for the pretrained models in v0.5.1); review those terms before redistributing or using them commercially.

## 2. Build and install

From PowerShell:

```powershell
cd "F:\VS Code\AI-wireless-build\VA"
& "F:\Downloads\gradle-8.7\bin\gradle.bat" testDebugUnitTest assembleDebug --no-daemon
```

Then install:

```powershell
& "F:\Downloads\platform-tools\adb.exe" install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

Grant microphone permission and notification permission when prompted. Open Voice Assistant and enable **Hey Jarvis offline wake word** while the app is visible. Android requires the user to start a microphone foreground service from an allowed app state; the ongoing notification and Stop action are intentional.

## 3. How it behaves

- The Hey Jarvis classifier runs locally after the models are packaged into the app; wake-word audio is not sent to a wake-word cloud service.
- When the phrase is detected, the notification changes to **Hey Jarvis heard you**. Tap that notification to open the app and start command speech recognition. The notification tap is deliberate because background activity launches and background speech recognition are restricted across Android versions.
- After the command response completes, the wake-word detector is re-armed if the service remains enabled. While a sensitive action awaits confirmation, wake-word capture stays paused so it cannot compete for microphone input. Existing confirmation UI and local action allowlists remain authoritative.
- The command speech recognizer is Android's `SpeechRecognizer`, which may use the device's selected recognition provider and may require internet. Only wake-word detection is guaranteed to run locally.
- Android may stop or restrict the service after force-stop, permission revocation, battery restrictions, or device/OEM policy. This is not equivalent to privileged, hardware-DSP “Hey Google” always-on support.

## Troubleshooting

- **Model download fails**: build with internet access and rerun `assembleDebug`. Check whether GitHub release downloads are blocked by your network.
- **Wake-word engine fails**: verify microphone permission is granted and inspect Logcat for the `HeyJarvisWakeWord` tag.
- **No notification**: check Android notification settings for Voice Assistant. The service uses a persistent notification by design.
