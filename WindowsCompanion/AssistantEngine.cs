using System.Diagnostics;
using System.Globalization;
using System.Speech.Recognition;
using System.Speech.Synthesis;

namespace Jarvis.Windows;

public sealed class AssistantEngine : IDisposable
{
    private readonly SpeechSynthesizer _synthesizer = new();
    private SpeechRecognitionEngine? _recognizer;

    public event Action<string, string>? LogMessage;
    public event Action<string>? StatusChanged;
    public event Action<string>? CommandRecognized;

    public bool VoiceInputAvailable => _recognizer is not null;

    private static readonly string[] RecognizedPhrases =
    [
        "what time is it",
        "tell me the time",
        "what is today's date",
        "what is the date",
        "open notepad",
        "open calculator",
        "open file explorer",
        "open explorer",
        "open settings",
        "open browser",
        "stop listening",
        "cancel",
        "jarvis what time is it",
        "jarvis open notepad",
        "jarvis open calculator",
        "jarvis open file explorer"
    ];

    public AssistantEngine()
    {
        _synthesizer.SetOutputToDefaultAudioDevice();
        try
        {
            _recognizer = new SpeechRecognitionEngine();
            var choices = new Choices(RecognizedPhrases);
            var builder = new GrammarBuilder { Culture = _recognizer.RecognizerInfo.Culture };
            builder.Append(choices);
            _recognizer.LoadGrammar(new Grammar(builder));
            _recognizer.SetInputToDefaultAudioDevice();
            _recognizer.SpeechRecognized += (_, args) =>
            {
                if (args.Result.Confidence >= 0.55f)
                    CommandRecognized?.Invoke(args.Result.Text);
            };
            _recognizer.RecognizeCompleted += (_, _) => StatusChanged?.Invoke("Ready • local commands only");
        }
        catch (Exception ex)
        {
            _recognizer?.Dispose();
            _recognizer = null;
            StatusChanged?.Invoke("Voice engine unavailable • typing still works");
            LogMessage?.Invoke("SYSTEM", "Windows speech recognition could not initialize: " + ex.Message);
        }
    }

    public void StartListening()
    {
        if (_recognizer is null)
        {
            StatusChanged?.Invoke("Voice engine unavailable • typing still works");
            LogMessage?.Invoke("SYSTEM", "Enable a compatible Windows speech-recognition language to use microphone input.");
            return;
        }

        try
        {
            _recognizer.SetInputToDefaultAudioDevice();
            _recognizer.RecognizeAsync(RecognizeMode.Multiple);
            StatusChanged?.Invoke("Listening • local Windows speech engine");
        }
        catch (InvalidOperationException ex)
        {
            StatusChanged?.Invoke("Already listening or microphone unavailable");
            LogMessage?.Invoke("SYSTEM", ex.Message);
        }
        catch (Exception ex)
        {
            StatusChanged?.Invoke("Microphone unavailable");
            LogMessage?.Invoke("SYSTEM", ex.Message);
        }
    }

    public void StopListening()
    {
        try
        {
            _recognizer?.RecognizeAsyncCancel();
        }
        catch (InvalidOperationException)
        {
            // Already stopped; this is an idempotent user action.
        }
        StatusChanged?.Invoke("Ready • local commands only");
    }

    public void ExecuteCommand(string input)
    {
        var command = string.Join(" ", input.Trim().ToLowerInvariant().Split(' ', StringSplitOptions.RemoveEmptyEntries))
            .TrimEnd('.', '?', '!', ',');
        if (command.StartsWith("jarvis ", StringComparison.Ordinal))
            command = command["jarvis ".Length..];

        try
        {
            switch (command)
            {
                case "cancel":
                case "stop listening":
                    StopListening();
                    Reply("Listening stopped.");
                    return;
                case "what time is it":
                case "tell me the time":
                    Reply("It is " + DateTime.Now.ToString("h:mm tt", CultureInfo.CurrentCulture) + ".");
                    return;
                case "what is today's date":
                case "what is the date":
                    Reply("Today is " + DateTime.Now.ToString("dddd, dd MMMM yyyy", CultureInfo.CurrentCulture) + ".");
                    return;
                case "open notepad":
                    Launch("notepad.exe", "Notepad");
                    return;
                case "open calculator":
                    Launch("calc.exe", "Calculator");
                    return;
                case "open file explorer":
                case "open explorer":
                    Launch("explorer.exe", "File Explorer");
                    return;
                case "open settings":
                    Launch("ms-settings:", "Windows Settings");
                    return;
                case "open browser":
                    Launch("https://www.bing.com", "the browser");
                    return;
                default:
                    Reply("That command is not enabled yet. I can tell the time or date and open Notepad, Calculator, File Explorer, Settings, or the browser.");
                    return;
            }
        }
        catch (Exception ex)
        {
            LogMessage?.Invoke("SYSTEM", "The action failed: " + ex.Message);
            StatusChanged?.Invoke("Action failed");
        }
    }

    private void Launch(string target, string displayName)
    {
        Process.Start(new ProcessStartInfo(target) { UseShellExecute = true });
        Reply("Opening " + displayName + ".");
    }

    private void Reply(string text)
    {
        LogMessage?.Invoke("JARVIS", text);
        try
        {
            _synthesizer.SpeakAsyncCancelAll();
            _synthesizer.SpeakAsync(text);
        }
        catch (Exception ex)
        {
            LogMessage?.Invoke("SYSTEM", "Speech output unavailable: " + ex.Message);
        }
    }

    public void Dispose()
    {
        StopListening();
        _recognizer?.Dispose();
        _synthesizer.Dispose();
    }
}
