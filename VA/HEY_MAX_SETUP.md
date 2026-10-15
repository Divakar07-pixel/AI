# Hey Max offline wake-word setup

This project integrates Picovoice Porcupine in a user-started Android microphone foreground service. The wake phrase is **Hey Max**. The source code intentionally does not include the proprietary custom `.ppn` model or your account AccessKey.

## 1. Create the Android wake-word model

1. Create/sign in to an account at [Picovoice Console](https://console.picovoice.ai/).
2. Open **Porcupine** and create a custom wake word with the phrase exactly **Hey Max**.
3. Select **English** and the **Android** platform, then train and download the Android `.ppn` file.
4. Put the downloaded file in this exact path:
   `VA/app/src/main/assets/Hey-Max_android.ppn`
   Create the `assets` directory if it does not exist. If Console gives the file a different name, rename it to `Hey-Max_android.ppn`.
5. Do not use a model trained for another platform. Custom Porcupine models are platform-specific.

## 2. Configure the AccessKey locally

1. In Picovoice Console, copy your **AccessKey**.
2. Open `VA/local.properties` (create it if missing) and add this line, keeping any existing Android SDK or Supabase properties:

   ```properties
   PICOVOICE_ACCESS_KEY=paste_your_picovoice_access_key_here
   ```

3. Do not commit `local.properties` or paste the key into source code. The project .gitignore already excludes this file.
4. Gradle injects this value into `BuildConfig` for local builds. **Important:** a key embedded in a client APK can potentially be extracted; treat it as a client SDK credential and review Picovoice account terms/usage limits before distributing the APK.

## 3. Build and run

From PowerShell:

```powershell
cd "F:\VS Code\AI-wireless-build\VA"
& "F:\Downloads\gradle-8.7\bin\gradle.bat" testDebugUnitTest assembleDebug --no-daemon
```

Then install:

```powershell
& "F:\Downloads\platform-tools\adb.exe" install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

Grant microphone permission and notification permission when prompted. Open Voice Assistant and enable **Hey Max offline wake word** while the app is visible. Android requires the user to start a microphone foreground service from an allowed app state; the ongoing notification and Stop action are intentional.

## 4. How it behaves

- Porcupine performs wake-word inference on-device; raw microphone audio is not uploaded by the wake-word detector.
- Porcupine AccessKey validation may need internet when the engine initializes. Once initialized, wake-word detection itself is local.
- When the phrase is detected, the notification changes to **Hey Max heard you**. Tap that notification to open the app and start command speech recognition. This notification tap is deliberate because background activity launches and background speech recognition are restricted across Android versions.
- After the command response completes, the wake-word detector is re-armed if the service remains enabled. While a sensitive action awaits confirmation, wake-word capture stays paused so it cannot compete for microphone input. Existing confirmation UI remains authoritative.
- The command speech recognizer is Android's `SpeechRecognizer`, which may use the device's selected recognition provider and may require internet. Only wake-word detection is guaranteed to run locally.
- Android may stop or restrict the service after force-stop, permission revocation, battery restrictions, or device/OEM policy. This is not equivalent to privileged, hardware-DSP “Hey Google” always-on support.

## Troubleshooting

- **“add PICOVOICE_ACCESS_KEY”**: check the property name in `VA/local.properties`, then rebuild.
- **“add Hey-Max_android.ppn”**: verify the model is in `VA/app/src/main/assets/` and the exact filename matches.
- **Porcupine initialization failed**: verify the key is valid, the model was trained for Android, and the device can reach Picovoice for key validation.
- **No notification**: check Android notification settings for Voice Assistant. The service uses a persistent notification by design.
