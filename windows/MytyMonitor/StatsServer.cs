using System.Collections.Concurrent;
using System.Net;
using System.Text;
using System.Text.Json;

namespace MytyMonitor;

/// <summary>
/// Samples the hardware once a second and serves the latest snapshot as JSON:
///   GET /api/stats  -> Snapshot
///   GET /api/hello  -> { app, v, name, port }  (used by the phone to check a host)
/// The phone reaches this over Wi-Fi, USB tethering, or a USB cable via "adb reverse" (127.0.0.1 on the phone).
/// </summary>
public sealed class StatsServer : IDisposable
{
    public const int DefaultPort = 5317;

    public static readonly JsonSerializerOptions Json = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    private readonly HardwareSampler _sampler = new();
    private readonly HttpListener _listener = new();
    private readonly CancellationTokenSource _cts = new();
    private readonly ConcurrentDictionary<string, DateTime> _clients = new();
    private volatile byte[] _latestJson = "{}"u8.ToArray();

    public int Port { get; }
    public bool LanEnabled { get; private set; }
    public Snapshot? Latest { get; private set; }
    public event Action<Snapshot>? Sampled;

    public StatsServer(int port = DefaultPort)
    {
        Port = port;
    }

    public void Start()
    {
        // "+" binds every interface (needs admin, which we have). Fall back to loopback only
        // so USB via adb still works if the LAN binding is refused.
        try
        {
            _listener.Prefixes.Add($"http://+:{Port}/");
            _listener.Start();
            LanEnabled = true;
        }
        catch (HttpListenerException)
        {
            _listener.Prefixes.Clear();
            _listener.Prefixes.Add($"http://localhost:{Port}/");
            _listener.Prefixes.Add($"http://127.0.0.1:{Port}/");
            _listener.Start();
            LanEnabled = false;
        }

        new Thread(SampleLoop) { IsBackground = true, Name = "sampler" }.Start();
        _ = AcceptLoop();
    }

    /// <summary>Distinct phones that requested stats in the last few seconds.</summary>
    public IReadOnlyList<string> ActiveClients()
    {
        var cutoff = DateTime.UtcNow.AddSeconds(-4);
        return _clients.Where(kv => kv.Value > cutoff).Select(kv => kv.Key).OrderBy(k => k).ToList();
    }

    private void SampleLoop()
    {
        while (!_cts.IsCancellationRequested)
        {
            var started = Environment.TickCount64;
            try
            {
                var snap = _sampler.Sample();
                Latest = snap;
                _latestJson = JsonSerializer.SerializeToUtf8Bytes(snap, Json);
                Sampled?.Invoke(snap);
            }
            catch (Exception ex)
            {
                Log.Write("sample failed: " + ex);
            }
            var wait = 1000 - (int)(Environment.TickCount64 - started);
            if (wait > 0) _cts.Token.WaitHandle.WaitOne(wait);
        }
    }

    private async Task AcceptLoop()
    {
        while (!_cts.IsCancellationRequested)
        {
            HttpListenerContext ctx;
            try { ctx = await _listener.GetContextAsync(); }
            catch { break; } // listener stopped
            _ = Task.Run(() => Handle(ctx));
        }
    }

    private void Handle(HttpListenerContext ctx)
    {
        var res = ctx.Response;
        try
        {
            res.Headers["Access-Control-Allow-Origin"] = "*";
            res.Headers["Cache-Control"] = "no-store";
            var path = ctx.Request.Url?.AbsolutePath.TrimEnd('/') ?? "";
            byte[] body;
            switch (path)
            {
                case "/api/stats":
                    _clients[ctx.Request.RemoteEndPoint.Address.ToString()] = DateTime.UtcNow;
                    body = _latestJson;
                    res.ContentType = "application/json";
                    break;
                case "/api/hello":
                    body = Encoding.UTF8.GetBytes(Discovery.HelloJson(Port));
                    res.ContentType = "application/json";
                    break;
                case "":
                    body = Encoding.UTF8.GetBytes(
                        "<html><body style=\"background:#002A8C;color:#fff;font-family:sans-serif\">" +
                        "<h2>Myty Monitor is running</h2><p>Open the Myty Monitor app on your phone, " +
                        "or see <a style=\"color:#fff\" href=\"/api/stats\">/api/stats</a>.</p></body></html>");
                    res.ContentType = "text/html";
                    break;
                default:
                    res.StatusCode = 404;
                    body = [];
                    break;
            }
            res.ContentLength64 = body.Length;
            res.OutputStream.Write(body);
        }
        catch (Exception ex)
        {
            Log.Write("request failed: " + ex.Message);
        }
        finally
        {
            try { res.Close(); } catch { }
        }
    }

    public void Dispose()
    {
        _cts.Cancel();
        try { _listener.Stop(); } catch { }
        _sampler.Dispose();
    }
}

internal static class Log
{
    private static readonly string Path = System.IO.Path.Combine(Settings.Dir, "log.txt");
    private static readonly object Gate = new();

    public static void Write(string line)
    {
        lock (Gate)
        {
            try
            {
                if (File.Exists(Path) && new FileInfo(Path).Length > 1_000_000) File.Delete(Path);
                File.AppendAllText(Path, $"{DateTime.Now:yyyy-MM-dd HH:mm:ss} {line}{Environment.NewLine}");
            }
            catch { }
        }
    }
}
