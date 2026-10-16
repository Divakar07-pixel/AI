# Jarvis backend setup

A dedicated Supabase project has been created for Jarvis:

- Project reference: `kzixmmmdvmunnxusyoen`
- Project URL: `https://kzixmmmdvmunnxusyoen.supabase.co`
- Region: `ap-south-1`
- Edge Function: `assistant-chat`
- JWT verification: enabled

The function is deployed, but hosted AI replies remain unavailable until a provider key is added as a Supabase secret. No provider key is stored in this repository or Android APK.

## Configure the Android client locally

In `VA/local.properties` (ignored by Git; keep any existing `sdk.dir` line), add:

```properties
SUPABASE_URL=https://kzixmmmdvmunnxusyoen.supabase.co
SUPABASE_ANON_KEY=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY
```

Get the project's publishable key from the Supabase dashboard's API Keys page. A publishable/anon key is intended for client-side use; it is not a provider secret. Never put `GEMINI_API_KEY` in Android code, `local.properties`, or the APK.

## Enable anonymous sessions

The Android client currently uses Supabase Auth anonymous sign-in to obtain a JWT for the Edge Function. In the Supabase dashboard, open **Authentication → Sign In / Providers** and enable **Anonymous Sign-Ins**. Keep Edge Function JWT verification enabled. Review Auth rate limits before sharing the app.

## Configure hosted AI (requires a provider account)

In Supabase Dashboard → **Edge Functions → Secrets**, add:

- Name: `GEMINI_API_KEY`
- Value: your Google AI Studio Gemini API key

Optionally add `GEMINI_MODEL` (default: `gemini-2.5-flash`) if you need a model available to your account. The key belongs only in the server-side secrets store. Gemini usage is subject to your Google AI account's quota and pricing; check those terms before use. Never commit the key to Git or include it in the APK.

## Build and verify

From the `VA` directory with JDK 17 and Gradle 8.7:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --stacktrace
```

The repository's wrapper JAR is currently broken in some environments; the GitHub Actions workflow uses an official Gradle 8.7 distribution directly.

## Current capability boundary

The Edge Function can provide conversational AI and normalize phrasing into commands the Android app already supports. The app reparses normalized commands locally; AI output is not executed directly. A general-purpose code-editing/deployment agent, remote Windows bridge, and working always-on “Hey Jarvis” detector are not implemented yet. Those require separate tool integrations, permissions, tests, and approval gates.
