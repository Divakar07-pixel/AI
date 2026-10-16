using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;

namespace Jarvis.Windows;

public partial class MainWindow : Window
{
    private readonly AssistantEngine _assistant = new();
    private int _messageCount;

    public MainWindow()
    {
        InitializeComponent();
        _assistant.LogMessage += (speaker, message) => Dispatcher.Invoke(() => AppendMessage(speaker, message));
        _assistant.StatusChanged += message => Dispatcher.Invoke(() => StatusText.Text = message);
        _assistant.CommandRecognized += command => Dispatcher.Invoke(() => ExecuteCommand(command));
    }

    private void Window_Loaded(object sender, RoutedEventArgs e)
    {
        AppendMessage("JARVIS", "Desktop assistant ready. Try “What time is it?” or “Open Notepad”.");
        if (!_assistant.VoiceInputAvailable)
            StatusText.Text = "Voice engine unavailable • typing still works";
    }

    private void ListenButton_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            _assistant.StartListening();
        }
        catch (Exception ex)
        {
            StatusText.Text = "Could not start microphone";
            AppendMessage("SYSTEM", ex.Message);
        }
    }

    private void StopButton_Click(object sender, RoutedEventArgs e)
    {
        _assistant.StopListening();
    }

    private void SendButton_Click(object sender, RoutedEventArgs e)
    {
        ExecuteCommand(CommandInput.Text);
        CommandInput.Clear();
    }

    private void CommandInput_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter)
        {
            e.Handled = true;
            SendButton_Click(sender, e);
        }
    }

    private void Suggestion_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button { Tag: string command })
            ExecuteCommand(command);
    }

    private void ExecuteCommand(string command)
    {
        if (string.IsNullOrWhiteSpace(command))
            return;

        AppendMessage("YOU", command.Trim());
        _assistant.ExecuteCommand(command);
        CommandInput.Clear();
    }

    private void AppendMessage(string speaker, string message)
    {
        TranscriptBox.AppendText("[" + DateTime.Now.ToString("HH:mm:ss") + "] " + speaker + ": " + message + Environment.NewLine + Environment.NewLine);
        TranscriptBox.ScrollToEnd();
        _messageCount++;
        MessageCountText.Text = _messageCount.ToString() + " entries";
    }

    private void Window_Closed(object? sender, EventArgs e)
    {
        _assistant.Dispose();
    }
}
