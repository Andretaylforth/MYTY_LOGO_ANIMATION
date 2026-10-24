using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;

namespace MytyMonitor;

/// <summary>
/// Lets the phone find this PC on the local network without typing an IP.
/// Every 2s we broadcast a small JSON beacon to UDP 5318, and we answer "MYTY?" queries directly.
/// </summary>
public sealed class Discovery : IDisposable
{
    public const int UdpPort = 5318;
    private const string Query = "MYTY?";

    private readonly int _httpPort;
    private readonly CancellationTokenSource _cts = new();
    private UdpClient? _udp;

    public Discovery(int httpPort)
    {
        _httpPort = httpPort;
    }

    public static string HelloJson(int port) => JsonSerializer.Serialize(new
    {
        app = "myty-monitor",
        v = 1,
        name = Environment.MachineName,
        port,
    });

    public void Start()
    {
        try
        {
            _udp = new UdpClient(AddressFamily.InterNetwork);
            _udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            _udp.Client.Bind(new IPEndPoint(IPAddress.Any, UdpPort));
            _udp.EnableBroadcast = true;
        }
        catch (Exception ex)
        {
            Log.Write("discovery disabled: " + ex.Message);
            return;
        }
        _ = ReceiveLoop();
        _ = BeaconLoop();
    }

    private async Task BeaconLoop()
    {
        while (!_cts.IsCancellationRequested)
        {
            var payload = Encoding.UTF8.GetBytes(HelloJson(_httpPort));
            foreach (var target in BroadcastAddresses())
            {
                try { await _udp!.SendAsync(payload, payload.Length, new IPEndPoint(target, UdpPort)); }
                catch { }
            }
            try { await Task.Delay(2000, _cts.Token); } catch { break; }
        }
    }

    private async Task ReceiveLoop()
    {
        while (!_cts.IsCancellationRequested)
        {
            try
            {
                var msg = await _udp!.ReceiveAsync(_cts.Token);
                if (!Encoding.UTF8.GetString(msg.Buffer).StartsWith(Query)) continue; // includes our own beacons
                var reply = Encoding.UTF8.GetBytes(HelloJson(_httpPort));
                await _udp.SendAsync(reply, reply.Length, msg.RemoteEndPoint);
            }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) { break; }
            catch { }
        }
    }

    /// <summary>255.255.255.255 plus each adapter's subnet broadcast (more reliable with several adapters).</summary>
    private static IEnumerable<IPAddress> BroadcastAddresses()
    {
        yield return IPAddress.Broadcast;
        foreach (var (ip, mask) in LocalIPv4())
        {
            var a = ip.GetAddressBytes();
            var m = mask.GetAddressBytes();
            var b = new byte[4];
            for (var i = 0; i < 4; i++) b[i] = (byte)(a[i] | ~m[i]);
            yield return new IPAddress(b);
        }
    }

    public static IEnumerable<(IPAddress ip, IPAddress mask)> LocalIPv4()
    {
        foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (nic.OperationalStatus != OperationalStatus.Up) continue;
            if (nic.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
            foreach (var u in nic.GetIPProperties().UnicastAddresses)
            {
                if (u.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                if (u.Address.ToString().StartsWith("169.254.")) continue; // no DHCP lease
                yield return (u.Address, u.IPv4Mask);
            }
        }
    }

    public void Dispose()
    {
        _cts.Cancel();
        _udp?.Dispose();
    }
}
