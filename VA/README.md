# Voice Assistant (Android Native)

A reliable, privacy-first native voice assistant for Android phones built in **Kotlin** using **Jetpack Compose** and **Material 3**.

---

## 1. Project Overview & Philosophy

The **Voice Assistant** is built as a grounded, dependable prototype for your Android device. It uses native Android platform APIs (`SpeechRecognizer`, `TextToSpeech`, and Intent system) rather than third-party cloud services.

### Core Principles
- **No Grand Claims**: The assistant does not claim to control every app or perform arbitrary actions. It strictly adheres to an explicit, safe allowlist.
- **Push-to-Talk Primary**: Voice commands are supported when you open the app. Background wake-word detection is not implemented.
- **Explicit Confirmation Gates**: Asks for confirmation before sending messages, placing calls, deleting data, making purchases, or changing security settings.
- **Privacy-aware design**: This app does not write microphone audio to files. Android's selected speech-recognition provider may process audio according to that provider's own policies. Optional AI chat sends text conversation history to the configured Supabase Edge Function.
- **Zero Stealth Operations**: No arbitrary shell scripts, no accessibility service taps, and no permission bypasses.
- **Emergency Stop / Cancel**: Visible controls to immediately halt speech recognition, silence spoken audio, or abort pending actions.

---

## 2. Version 1 Features (Allowlist of Actions)

| Category | Example Voice Commands | Android API / Intent Used |
| :--- | :--- | :--- |
| **Time & Date** | *"What time is it?"*, *"Tell me the time"*, *"What is today's date?"*, *"What day is it?"* | In-app speech output (`TextToSpeech`) + Visual chat transcript |
| **Timers** | *"Set a timer for 5 minutes"*, *"Timer for 30 seconds"*, *"Set timer for 1 hour"* | `AlarmClock.ACTION_SET_TIMER` |
| **Alarms** | *"Set an alarm for 7:30 AM"*, *"Wake me up at 6 AM"*, *"Alarm for 8 PM"* | `AlarmClock.ACTION_SET_ALARM` |
| **Open Apps** | *"Open Camera"*, *"Open YouTube"*, *"Launch Chrome"*, *"Open Maps"*, *"Open Calculator"*, *"Open Clock"*, *"Open Calendar"* | `MediaStore.ACTION_IMAGE_CAPTURE`, `PackageManager.getLaunchIntentForPackage()` |
| **Settings** | *"Open Wi-Fi settings"*, *"Open Bluetooth"*, *"Open Display settings"*, *"Open Battery settings"*, *"Open Sound settings"*, *"Open App settings"* | `Settings.ACTION_WIFI_SETTINGS`, `Settings.ACTION_BLUETOOTH_SETTINGS`, `Settings.ACTION_DISPLAY_SETTINGS`, `Settings.ACTION_SOUND_SETTINGS`, etc. |

---

## 3. Mandatory Confirmation for Sensitive Actions

To safeguard your device and personal data, the assistant detects sensitive intent and pauses execution until you explicitly confirm:

1. **Sending Messages / SMS**:
   - Voice trigger: *"Send a message to Alice saying I am on my way"* or *"Text Bob saying Running late"*
   - Action: Displays a high-visibility confirmation card with recipient and text body. The assistant speaks: *"Confirm sending message to 'Alice' saying: 'I am on my way'. Say 'confirm' to proceed or 'cancel' to abort."*
   - Execution: Once confirmed, safely launches Android's messaging app with the text pre-populated so you review before the final tap.
2. **Placing Calls**:
   - Voice trigger: *"Call Mom"* or *"Dial 9876543210"*
   - Action: Asks for confirmation via voice or screen button.
   - Execution: Launches `Intent.ACTION_DIAL` (phone dialer opens with number pre-filled).
3. **Deleting Data**:
   - Voice trigger: *"Delete my photos"*, *"Clear data"*
   - Action: Warns user and prevents automated background data deletion.
4. **Purchases**:
   - Voice trigger: *"Buy subscription"*, *"Order groceries"*
   - Action: Explains that direct automated purchases are disabled for security.
5. **Security & Privacy Changes**:
   - Voice trigger: *"Change password"*, *"Disable lock screen"*
   - Action: Opens Android Security Settings screen for manual configuration.

---

## 4. Locked-Screen Wake-Word Investigation & Limitations

### The Technical Reality on Modern Android (API 30+ / Android 11, 12, 13, 14, 15)

The requirement to support a user-enabled wake-word mode when the device is locked was investigated against Android platform security and battery specifications:

1. **Background Microphone Restrictions**:
   Starting with Android 11 and strengthened in Android 14 (`FOREGROUND_SERVICE_MICROPHONE`), the Android OS strictly prohibits third-party background applications from capturing microphone audio unless running as a foreground service with a visible notification.
2. **Microphone Privacy Indicators**:
   Android 12+ displays a system-level green microphone dot whenever any app accesses the microphone.
3. **Doze Mode & Screen-Off Microphone Silencing**:
   When an Android device screen is locked and remains idle, the operating system enters **Doze Mode**. Android kernels actively suspend or silence background audio recording streams to protect user privacy and prevent battery drain.
4. **Hardware DSP / Sound Trigger HAL**:
   Always-on, battery-efficient hotword detection (such as *"Hey Google"*) relies on low-power hardware Digital Signal Processors (DSP) integrated into the device SoC. Third-party applications cannot access this hardware layer unless they are granted the privileged System Assistant role (`RoleManager.ROLE_ASSISTANT`) and OEM system image integration.

### Our Compliant Implementation:
- **Foreground-Service Demo**: The app includes a foreground-service lifecycle demonstration, but it does not capture audio or detect a wake word.
- **Persistent Notification**: When enabled, the OS displays an ongoing notification that explicitly says wake-word detection is not implemented.
- **Clear Controls**: The user can stop the service through the in-app switch or notification action.
- **Push-to-Talk Fallback**: Because OS battery savers and OEM task killers (e.g. Samsung OneUI, Xiaomi MIUI) will eventually suspend continuous background microphone streaming when locked, **push-to-talk in the app is maintained as the primary, reliable, zero-battery-drain interface**.

---

## 5. Required Android Permissions

| Permission | Purpose | When Requested |
| :--- | :--- | :--- |
| `android.permission.RECORD_AUDIO` | Required for speech-to-text input | Runtime permission dialog upon tapping the microphone |
| `android.permission.POST_NOTIFICATIONS` | Required on Android 13+ (API 33+) for persistent foreground service notification | Runtime permission dialog when toggling the background service |
| `android.permission.FOREGROUND_SERVICE` | Required to run foreground services | Manifest |
| `android.permission.FOREGROUND_SERVICE_MICROPHONE` | Required on Android 14+ (API 34+) to capture mic audio in a foreground service | Manifest |
| `android.permission.WAKE_LOCK` | Keeps CPU active during short voice recognition cycles | Manifest |
| `com.android.alarm.permission.SET_ALARM` | Required to invoke standard clock timers and alarms | Manifest |

---

## 6. Architecture & Directory Structure

```
D:\Automation\VA\
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml          # Permissions, queries, activities, services
│   │   │   ├── java/com/example/voiceassistant/
│   │   │   │   ├── MainActivity.kt          # Compose host activity & runtime permissions
│   │   │   │   ├── domain/
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── AssistantAction.kt  # Allowlist actions & sensitive actions
│   │   │   │   │   │   └── AssistantState.kt   # UI states & chat messages
│   │   │   │   │   └── parser/
│   │   │   │   │       └── CommandParser.kt    # NLP regex allowlist & confirmation parser
│   │   │   │   ├── data/
│   │   │   │   │   ├── ActionExecutor.kt       # Intent execution (Timers, Apps, Settings)
│   │   │   │   │   ├── SpeechRecognizerManager.kt # Native SpeechRecognizer wrapper
│   │   │   │   │   └── TextToSpeechManager.kt     # Native TextToSpeech manager
│   │   │   │   ├── service/
│   │   │   │   │   ├── VoiceAssistantForegroundService.kt # Android 14 mic foreground service
│   │   │   │   │   └── StopServiceReceiver.kt             # Notification Stop button receiver
│   │   │   │   └── ui/
│   │   │   │       ├── MainViewModel.kt        # StateFlow & business logic orchestration
│   │   │   │       ├── VoiceAssistantScreen.kt # Main Jetpack Compose screen
│   │   │   │       ├── theme/                  # Material 3 colors, typography, theme
│   │   │   │       └── components/             # Reusable Compose components
│   │   │   │           ├── ActionCard.kt       # Displays recognized speech & planned action
│   │   │   │           ├── ConfirmationDialog.kt # High-risk action confirmation card
│   │   │   │           ├── MicButton.kt        # Push-to-talk button & emergency stop
│   │   │   │           ├── ChatTranscript.kt   # Conversation history list
│   │   │   │           ├── BackgroundWakeWordCard.kt # Service toggle & OS limitation notes
│   │   │   │           ├── PrivacyDisclaimerDialog.kt # Privacy & security info dialog
│   │   │   │           └── StatusHeader.kt     # Header with state badges
│   │   │   └── res/                            # Vector drawables, colors, strings, themes
│   │   └── test/java/com/example/voiceassistant/
│   │       ├── CommandParserTest.kt            # Unit tests for NLP parser & allowlist
│   │       └── AssistantStateTest.kt           # Unit tests for state models
│   ├── build.gradle.kts                        # App module Gradle configuration
│   └── proguard-rules.pro                      # ProGuard rules
├── gradle/
│   ├── libs.versions.toml                      # Modern Gradle Version Catalog
│   └── wrapper/
│       ├── gradle-wrapper.jar                  # Gradle 8.7 wrapper jar
│       └── gradle-wrapper.properties
├── build.gradle.kts                            # Root Gradle build script
├── settings.gradle.kts                         # Root Gradle settings
├── gradlew                                     # Linux/macOS wrapper script
├── gradlew.bat                                 # Windows wrapper script
└── README.md
```

---

## 7. Build and Installation Steps

### Prerequisites
- **Android Studio**: Android Studio Hedgehog (2023.1.1), Iguana (2023.2.1), Jellyfish (2023.3.1), Koala (2024.1.1), or Ladybug (2024.2.1+).
- **JDK**: Java 17 (bundled automatically with Android Studio as JBR).
- **Device or Emulator**: Android 8.0 (API 26) through Android 15 (API 35). Recommended: Android 13 or 14 physical device or Google Play emulator.

---

### Step 1: Open in Android Studio
1. Launch **Android Studio**.
2. Select **Open** (or `File > Open...`).
3. Browse to and select `D:\Automation\VA`.
4. Allow Android Studio to sync the Gradle project automatically. The project uses Gradle 8.7 and AGP 8.4.2 via the included Version Catalog (`gradle/libs.versions.toml`).

---

### Step 2: Build the Debug APK

#### Method A: Using Android Studio UI
1. In the top toolbar, go to **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
2. Once the build completes, a notification will appear in the bottom right: *"APK(s) generated successfully for 1 module"*.
3. Click the **locate** link to open the folder containing `app-debug.apk`.
   The file path is:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

#### Method B: Using Gradle Command Line
Open a terminal in `D:\Automation\VA` and execute:
- **Windows (PowerShell or Command Prompt)**:
  ```powershell
  .\gradlew.bat assembleDebug
  ```
- **macOS / Linux**:
  ```bash
  ./gradlew assembleDebug
  ```

---

### Step 3: Install on Device or Emulator

#### Method A: Direct Run from Android Studio
1. Connect your Android phone via USB and enable **USB Debugging** (in `Settings > Developer Options`), or start an Android Virtual Device (AVD).
2. Select your device from the device dropdown menu in Android Studio.
3. Click the green **Run 'app'** button (or press `Shift + F10`).
4. The app installs and launches automatically.

#### Method B: Installing via ADB (Android Debug Bridge)
If you generated the APK and want to install it over ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 8. Running the Unit Tests

The project includes unit tests verifying:
- Natural language parsing of time and date queries
- Parsing timers (seconds, minutes, hours)
- Parsing alarms (morning, afternoon, 12-hour AM/PM and 24-hour formats)
- App launching and settings screen mapping
- Sensitive action recognition (sending messages, making calls, deletions, purchases)
- User confirmation voice responses (*"Yes"*, *"Confirm"*, *"No"*, *"Cancel"*)
- Safe handling of unrecognized commands

### Run Tests in Android Studio:
1. In the Project pane, navigate to:
   `app/src/test/java/com/example/voiceassistant/`
2. Right-click on `CommandParserTest` and select **Run 'CommandParserTest'**.

### Run Tests from Command Line:
```powershell
.\gradlew.bat testDebugUnitTest
```

---

## 9. Privacy Policy & Security Specifications

1. **Audio Handling**: Microphone audio is captured solely during an active listening session and passed directly in memory to Android's `SpeechRecognizer`. Raw audio is never written to disk, cached, or transmitted to any remote servers.
2. **Offline Local First**: The allowlist actions (time, date, app launching, timers, alarms, and settings) run entirely on-device using local Android Intents.
3. **No Hidden Background Activity**: The app does not attempt to inspect screen contents, abuse accessibility services, simulate touch events, or execute arbitrary shell commands.
4. **Third-Party Integrations**: Laptop or network companion integration is intentionally excluded from Version 1 to maintain a self-contained, auditable native Android codebase.


## 7. Optional AI conversation setup

General questions that are not recognized as device commands are routed to an AI chat endpoint. Device commands are still parsed and handled locally first; the AI response cannot execute device actions.

The included implementation uses a Supabase Edge Function and OpenAI-compatible chat completion. **The provider API key is stored only as a Supabase secret, never in the Android app.**

### Configure the Android app

In `VA/local.properties` (this file is machine-local and must not be committed), add:

```properties
sdk.dir=C\\:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY
```

Keep your existing `sdk.dir` value if it is already present; add only the two Supabase properties. The Supabase anon/publishable key is designed for client use with appropriate project security. **Do not add `OPENAI_API_KEY` to this file or to the APK.**

### Configure Supabase

1. Create or select a Supabase project.
2. Enable anonymous sign-ins in **Authentication → Sign In / Providers**.
3. From the repository root, install the Supabase CLI and link the project.
4. Set the provider key as a server secret: `supabase secrets set OPENAI_API_KEY=your_provider_key`.
5. Optionally set the model: `supabase secrets set OPENAI_MODEL=gpt-4o-mini`.
6. Deploy the function: `supabase functions deploy assistant-chat`. Keep JWT verification enabled so the function requires a valid Supabase session.
7. Review Supabase Auth rate limits and usage monitoring before sharing the app publicly. Anonymous users can consume AI quota, so do not treat the endpoint as abuse-proof.

The Android client signs in anonymously and calls `/functions/v1/assistant-chat`. If these settings are absent, the app continues to support local commands and explains that AI setup is missing.

### Local tests

From PowerShell in `F:\\VS Code\\AI-main\\VA` (adjust to your clone path), run:

```powershell
& "F:\\Downloads\\gradle-8.7\\bin\\gradle.bat" -p . testDebugUnitTest assembleDebug --no-daemon --stacktrace
```

The repository's Gradle wrapper JAR currently cannot load `GradleWrapperMain`, so the local command above uses a separately installed Gradle 8.7 distribution. The GitHub Actions workflow in `.github/workflows/android-ci.yml` also downloads Gradle 8.7 directly, runs unit tests, and assembles a debug APK for pushes and pull requests touching `VA/`.

## 8. Known limitations

- Background wake-word detection is not implemented; the foreground-service switch is a lifecycle demo only.
- AI conversation requires a configured Supabase project, anonymous auth, deployed Edge Function, and a server-side provider key.
- AI replies are text-only. They do not execute Android actions. Supported commands continue through the local parser and explicit confirmation flow.
- Speech recognition behavior and app/intent availability depend on the Android device and its configured services.
