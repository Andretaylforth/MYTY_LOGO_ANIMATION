using System.Diagnostics;

namespace MytyMonitor;

/// <summary>
/// USB cable support. When a phone with USB debugging is plugged in, run
/// "adb reverse tcp:5317 tcp:5317" so the phone can reach this PC at 127.0.0.1:5317 over the cable.
/// </summary>
public sealed class AdbBridge : IDisposable
{
    private readonly int _port;
    private readonly CancellationTokenSource _cts = new();
    private readonly HashSet<string> _bridged = new();

    public string? AdbPath { get; private set; }
    public string Status { get; private set; } = "Looking for adb…";

    public AdbBridge(int port)
    {
        _port = port;
    }

    public void Start() => new Thread(Loop) { IsBackground = true, Name = "adb" }.Start();

    private void Loop()
    {
        while (!_cts.IsCancellationRequested)
        {
            try { Tick(); }
            catch (Exception ex) { Status = "adb error: " + ex.Message; }
            _cts.Token.WaitHandle.WaitOne(3000);
        }
    }

    private void Tick()
    {
        AdbPath ??= FindAdb();
        if (AdbPath == null)
        {
            Status = "USB: adb not found (Wi-Fi still works)";
            return;
        }

        var devices = Run("devices")
            .Split('\n')
            .Select(l => l.Trim().Split('\t'))
            .Where(p => p.Length == 2)
            .ToList();

        var ready = devices.Where(p => p[1] == "device").Select(p => p[0]).ToList();
        var unauthorized = devices.Any(p => p[1] == "unauthorized");

        _bridged.IntersectWith(ready);
        foreach (var serial in ready.Where(s => !_bridged.Contains(s)))
        {
            Run($"-s {serial} reverse tcp:{_port} tcp:{_port}");
            _bridged.Add(serial);
            Log.Write($"adb reverse set up for {serial}");
        }

        Status = ready.Count > 0 ? $"USB: phone connected ({string.Join(", ", ready)})"
               : unauthorized ? "USB: allow USB debugging on the phone"
               : "USB: no phone plugged in";
    }

    private string Run(string args)
    {
        var psi = new ProcessStartInfo(AdbPath!, args)
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
        };
        using var p = Process.Start(psi)!;
        var output = p.StandardOutput.ReadToEnd();
        p.WaitForExit(5000);
        return output;
    }

    private static string? FindAdb()
    {
        var candidates = new List<string>
        {
            Path.Combine(AppContext.BaseDirectory, "adb.exe"),
            Path.Combine(AppContext.BaseDirectory, "platform-tools", "adb.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                         "Android", "Sdk", "platform-tools", "adb.exe"),
        };
        var sdk = Environment.GetEnvironmentVariable("ANDROID_HOME") ?? Environment.GetEnvironmentVariable("ANDROID_SDK_ROOT");
        if (sdk != null) candidates.Add(Path.Combine(sdk, "platform-tools", "adb.exe"));
        foreach (var dir in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(Path.PathSeparator))
        {
            if (dir.Length > 0) candidates.Add(Path.Combine(dir.Trim('"'), "adb.exe"));
        }
        return candidates.FirstOrDefault(File.Exists);
    }

    public void Dispose() => _cts.Cancel();
}
