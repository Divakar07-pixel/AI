# J.A.R.V.I.S. Windows Companion

This is a separate Windows desktop client for the Android Jarvis project. It uses WPF for a resizable Windows UI, System.Speech for optional local command recognition, and Windows Text-to-Speech for spoken replies.

## Current capabilities

- Adaptive, resizable desktop layout.
- Type a command or explicitly start/stop microphone listening.
- Local time/date responses.
- Open Notepad, Calculator, File Explorer, Windows Settings, and the default browser.
- Local text-to-speech.
- An allowlist-based command executor; arbitrary shell commands, shutdown, deletion, purchases, and other sensitive operations are not run.
- Text input remains available if Windows speech recognition or a compatible language pack is unavailable.

Voice input is grammar-limited. It is not general dictation or an unrestricted AI agent. The commands in this starter client do not require an internet service.

## Build on Windows 10/11

Install the .NET 8 SDK and a Windows Desktop runtime. Open PowerShell in this folder and run the commands below:

    dotnet restore
    dotnet build -c Release
    dotnet run

If voice input is unavailable, enable a supported Windows speech-recognition language in Windows Settings. Text command input can still be used.

## Android integration status

This Windows client currently controls only its own Windows host. It does not yet send commands to the Android phone. The Android app stays a separate native application.

A cross-device bridge must be implemented as a separate security-reviewed feature before remote Android commands are enabled. It should require explicit device pairing, short-lived credentials, authenticated and encrypted traffic, a narrow command allowlist, per-action confirmation for sensitive operations, replay protection, and an obvious disconnect control. Do not expose an unauthenticated TCP/HTTP listener and do not forward arbitrary command text to the phone for execution.

## Trust and permissions

- Microphone listening begins only after pressing Start listening.
- Actions are limited to a small explicit allowlist in AssistantEngine.cs.
- The code does not run arbitrary PowerShell, CMD, or user-supplied process paths.
- Review the command allowlist before adding capabilities.
