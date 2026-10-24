using System.Diagnostics;
using System.Text.Json;

namespace MytyMonitor;

public sealed class Settings
{
    public static readonly string Dir =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "MytyMonitor");
    private static readonly string FilePath = Path.Combine(Dir, "settings.json");

    public bool StartMinimized { get; set; }
    /// <summary>Which version of the firewall rules has been installed; bump to replace old rules.</summary>
    public int FirewallRules { get; set; }

    public static Settings Load()
    {
        Directory.CreateDirectory(Dir);
        try { return JsonSerializer.Deserialize<Settings>(File.ReadAllText(FilePath)) ?? new(); }
        catch { return new(); }
    }

    public void Save()
    {
        try { File.WriteAllText(FilePath, JsonSerializer.Serialize(this)); } catch { }
    }
}

/// <summary>Windows integration that needs admin: firewall rule and elevated start-at-login.</summary>
internal static class SystemSetup
{
    private const string TaskName = "MytyMonitor";

    private const int FirewallRulesVersion = 2;

    public static void EnsureFirewallRule(Settings settings)
    {
        if (settings.FirewallRules >= FirewallRulesVersion) return;

        // The stats server runs on HttpListener, which is served by the http.sys driver (the "System"
        // process), so an exe-based rule never matches it. Open the ports instead. All profiles,
        // because Windows often marks home Wi-Fi as "Public".
        RunHidden("netsh", "advfirewall firewall delete rule name=\"Myty Monitor\"");
        RunHidden("netsh", "advfirewall firewall delete rule name=\"Myty Monitor (stats)\"");
        RunHidden("netsh", "advfirewall firewall delete rule name=\"Myty Monitor (discovery)\"");
        var ok = RunHidden("netsh",
            $"advfirewall firewall add rule name=\"Myty Monitor (stats)\" dir=in action=allow protocol=TCP localport={StatsServer.DefaultPort} profile=any") == 0;
        ok &= RunHidden("netsh",
            $"advfirewall firewall add rule name=\"Myty Monitor (discovery)\" dir=in action=allow protocol=UDP localport={Discovery.UdpPort} profile=any") == 0;
        if (ok)
        {
            settings.FirewallRules = FirewallRulesVersion;
            settings.Save();
        }
        else
        {
            Log.Write("couldn't add firewall rules");
        }
    }

    // A Run registry key can't launch an app that requires admin without a UAC prompt,
    // so start-at-login uses a scheduled task with highest privileges instead.
    public static bool IsStartupEnabled() => RunHidden("schtasks", $"/query /tn {TaskName}") == 0;

    public static void SetStartup(bool enabled)
    {
        if (enabled)
        {
            RunHidden("schtasks",
                $"/create /tn {TaskName} /tr \"\\\"{Environment.ProcessPath}\\\" --minimized\" /sc onlogon /rl highest /f");
            // schtasks defaults would stop the app after 72 hours and when a laptop goes on battery.
            RunHidden("powershell", "-NoProfile -Command \"Set-ScheduledTask -TaskName " + TaskName +
                " -Settings (New-ScheduledTaskSettingsSet -ExecutionTimeLimit 0 -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries)\"");
        }
        else
            RunHidden("schtasks", $"/delete /tn {TaskName} /f");
    }

    private static int RunHidden(string file, string args)
    {
        try
        {
            using var p = Process.Start(new ProcessStartInfo(file, args)
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
            })!;
            p.WaitForExit(10000);
            return p.ExitCode;
        }
        catch
        {
            return -1;
        }
    }
}
