namespace MytyMonitor;

internal static class Program
{
    public static bool StartHidden { get; private set; }

    [STAThread]
    private static void Main(string[] args)
    {
        using var mutex = new Mutex(true, "Global\\MytyMonitor", out var first);
        if (!first)
        {
            MessageBox.Show("Myty Monitor is already running. Look for it in the system tray.", "Myty Monitor");
            return;
        }

        StartHidden = args.Contains("--minimized");
        ApplicationConfiguration.Initialize();

        var settings = Settings.Load();
        SystemSetup.EnsureFirewallRule(settings);

        using var server = new StatsServer();
        using var discovery = new Discovery(server.Port);
        using var adb = new AdbBridge(server.Port);
        try
        {
            server.Start();
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Couldn't start on port {server.Port}: {ex.Message}", "Myty Monitor");
            return;
        }
        discovery.Start();
        adb.Start();

        Application.Run(new MainForm(server, adb, settings));
    }
}
