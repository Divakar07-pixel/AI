# Hey Jarvis wake-word integration status

Wake-word training and integration are paused until positive and negative audio with compatible commercial rights are approved.

The Android app currently has only a lifecycle demonstration and a detector interface boundary. The service does not capture microphone audio, load ONNX assets, or detect "Hey Jarvis". Manual in-app microphone input remains the supported voice interface.

## Why model downloads are disabled

A pretrained classifier's code/runtime license does not automatically clear the model weights or the rights to use their training corpus. The previously considered pretrained openWakeWord model is excluded because its published pretrained-model terms do not satisfy the project's commercial-use requirement. Gradle therefore does not fetch any wake-word model files or add an openWakeWord runtime dependency in this branch.

## Data to provide later

Keep the user-provided training workspace organized as follows:

- positive: consented and commercially usable "Hey Jarvis" recordings.
- negative: commercially cleared non-wake speech, background noise, and environmental audio.
- LICENSES_AND_CONSENT.md: sources, exact licenses/terms, attribution, redistribution permissions, and speaker consent.

Do not train or export until both positive and negative data have been reviewed. Do not send user recordings to third parties.

## Resume plan

1. Validate local WAV files and the documented rights.
2. Establish a reproducible training environment and a speaker-independent validation split.
3. Train a custom classifier and evaluate false accepts and misses on held-out audio.
4. Export ONNX only after confirming model distribution terms and runtime compatibility.
5. Integrate the detector behind WakeWordDetector and test on the Pixel 6a running Android 17.
6. Remove the lifecycle-only language only after end-to-end device tests demonstrate real detection.

From the Android project directory, the regular build and unit tests can be run with the installed Gradle distribution. No wake-word training dependencies are required for the current Android build.
