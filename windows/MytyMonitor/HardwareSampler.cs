using System.Diagnostics;
using System.Text.RegularExpressions;
using System.Net.NetworkInformation;
using System.Runtime.InteropServices;
using LibreHardwareMonitor.Hardware;

namespace MytyMonitor;

/// <summary>Reads CPU/GPU sensors (LibreHardwareMonitor), RAM, network, drives and top processes.</summary>
public sealed class HardwareSampler : IDisposable
{
    private readonly Computer _computer;
    private readonly ProcessSampler _procs = new();
    private readonly GpuProcessSampler _gpuProcs = new();
    private readonly Stopwatch _clock = Stopwatch.StartNew();

    private long _lastRx, _lastTx;
    private double _lastNetT;
    private DriveStats[] _drives = [];
    private double _lastDriveT = -100;

    public HardwareSampler()
    {
        _computer = new Computer
        {
            IsCpuEnabled = true,
            IsGpuEnabled = true,
            IsMotherboardEnabled = true, // fan headers live on the Super I/O chip
            IsControllerEnabled = true,  // AIO pumps / fan controllers
        };
        _computer.Open();
    }

    public Snapshot Sample()
    {
        foreach (var hw in _computer.Hardware) UpdateDeep(hw);

        var all = _computer.Hardware.ToList();
        var cpuHw = all.FirstOrDefault(h => h.HardwareType == HardwareType.Cpu);
        var gpus = all.Where(h => h.HardwareType is HardwareType.GpuNvidia or HardwareType.GpuAmd or HardwareType.GpuIntel)
            .OrderBy(IsIntegrated)                      // dedicated cards first
            .ThenByDescending(h => DedicatedMemory(h) ?? 0) // then the one with the most VRAM
            .ToList();
        var gpuHw = gpus.FirstOrDefault();

        var gpuByPid = _gpuProcs.Sample();

        return new Snapshot(
            V: 1,
            Host: Environment.MachineName,
            Time: DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            Cpu: ReadCpu(cpuHw, all),
            Gpu: gpuHw == null ? null : ReadGpu(gpuHw),
            Gpus: gpus.Select(g => new GpuInfo(g.Name, IsIntegrated(g))).ToList(),
            Ram: ReadRam(),
            Net: ReadNet(),
            Drives: ReadDrives(),
            Procs: _procs.Sample(gpuByPid));
    }

    private static void UpdateDeep(IHardware hw)
    {
        hw.Update();
        foreach (var sub in hw.SubHardware) UpdateDeep(sub);
    }

    private static IEnumerable<ISensor> SensorsDeep(IHardware hw) =>
        hw.Sensors.Concat(hw.SubHardware.SelectMany(SensorsDeep));

    private static double? Find(IHardware hw, SensorType type, params string[] names)
    {
        var sensors = hw.Sensors.Where(s => s.SensorType == type && s.Value.HasValue).ToList();
        foreach (var n in names)
        {
            var hit = sensors.FirstOrDefault(s => s.Name.Equals(n, StringComparison.OrdinalIgnoreCase));
            if (hit != null) return hit.Value;
        }
        return null;
    }

    private static double? Round(double? v, int digits = 0) => v.HasValue ? Math.Round(v.Value, digits) : null;

    private static CpuStats ReadCpu(IHardware? cpu, List<IHardware> all)
    {
        if (cpu == null) return new CpuStats("CPU", null, null, null, null, null, []);

        var temp = Find(cpu, SensorType.Temperature,
            "Core (Tctl/Tdie)", "CPU Package", "Package", "Tctl", "Tdie", "Core Average", "Core Max");
        temp ??= cpu.Sensors.Where(s => s.SensorType == SensorType.Temperature && s.Value > 0)
                            .Select(s => (double?)s.Value).Max();

        var clock = cpu.Sensors
            .Where(s => s.SensorType == SensorType.Clock && s.Name.StartsWith("Core", StringComparison.OrdinalIgnoreCase) && s.Value > 0)
            .Select(s => (double?)s.Value).Max();

        var power = Find(cpu, SensorType.Power, "Package", "CPU Package");

        return new CpuStats(
            cpu.Name,
            Round(Find(cpu, SensorType.Load, "CPU Total"), 1),
            Round(temp, 1),
            Round(clock),
            Round(ReadCpuFan(all)),
            Round(power, 1),
            ReadCores(cpu));
    }

    // "CPU Core #3", "Core #3", "P-Core #1", "E-Core #2", "Core #1 (CCD1)"...
    private static readonly Regex CoreName = new(@"\b([PE]-)?Core #(\d+)\b", RegexOptions.IgnoreCase);

    /// <summary>Per-core temperature, clock and load, in the order the CPU lists its cores.</summary>
    private static List<CoreStats> ReadCores(IHardware cpu)
    {
        var cores = new List<string>();
        var temp = new Dictionary<string, double>();
        var clock = new Dictionary<string, double>();
        var load = new Dictionary<string, double>();
        foreach (var sensor in cpu.Sensors)
        {
            if (sensor.Value is not { } v) continue;
            var name = sensor.Name;
            if (name.Contains("Thread", StringComparison.OrdinalIgnoreCase) ||
                name.Contains("Distance", StringComparison.OrdinalIgnoreCase) ||
                name.Contains("Effective", StringComparison.OrdinalIgnoreCase)) continue;
            var m = CoreName.Match(name);
            if (!m.Success) continue;
            var key = m.Groups[1].Value.ToUpperInvariant().TrimEnd('-') + m.Groups[2].Value; // "3", "P1", "E2"
            var target = sensor.SensorType switch
            {
                SensorType.Temperature => temp,
                SensorType.Clock => clock,
                SensorType.Load => load,
                _ => null,
            };
            if (target == null || target.ContainsKey(key)) continue;
            target[key] = v;
            if (!cores.Contains(key)) cores.Add(key);
        }
        return cores.Select(k => new CoreStats(k,
            temp.TryGetValue(k, out var t) ? Math.Round(t, 1) : null,
            clock.TryGetValue(k, out var c) ? Math.Round(c) : null,
            load.TryGetValue(k, out var l) ? Math.Round(l, 1) : null)).ToList();
    }

    private static double? ReadCpuFan(List<IHardware> all)
    {
        // Super I/O chips usually only label headers "Fan #1", "Fan #2"...; prefer anything that says CPU/pump.
        var fans = all.Where(h => h.HardwareType is HardwareType.Motherboard or HardwareType.EmbeddedController
                                                   or HardwareType.Cooler)
                      .SelectMany(SensorsDeep)
                      .Where(s => s.SensorType == SensorType.Fan && s.Value > 0)
                      .ToList();
        var named = fans.FirstOrDefault(s => s.Name.Contains("CPU", StringComparison.OrdinalIgnoreCase))
                    ?? fans.FirstOrDefault(s => s.Name.Contains("Pump", StringComparison.OrdinalIgnoreCase));
        return (named ?? fans.FirstOrDefault())?.Value;
    }

    /// <summary>
    /// Ryzen and Intel CPUs often have an integrated GPU as well as the graphics card, and both are reported.
    /// LibreHardwareMonitor doesn't say which is which for AMD, so go by the name, then by dedicated memory.
    /// </summary>
    internal static bool IsIntegrated(IHardware gpu)
    {
        if (gpu.HardwareType == HardwareType.GpuNvidia) return false;
        if (gpu.HardwareType == HardwareType.GpuIntel) return gpu.GetType().Name.Contains("Integrated");
        var name = gpu.Name;
        // Graphics cards: "Radeon RX 7900 XTX", "Radeon Pro W7800", "Radeon VII".
        if (Regex.IsMatch(name, @"\bRX\b|Radeon Pro|Radeon VII|FirePro|Instinct", RegexOptions.IgnoreCase)) return false;
        // Integrated: "AMD Radeon(TM) Graphics", "Radeon 780M Graphics", "Radeon 890M", "Radeon Vega 8 Graphics".
        if (Regex.IsMatch(name, @"Graphics\s*$|\b\d{3,4}M\b|\bVega\b", RegexOptions.IgnoreCase)) return true;
        return (DedicatedMemory(gpu) ?? 0) < 2048;
    }

    private static double? DedicatedMemory(IHardware gpu) =>
        Find(gpu, SensorType.SmallData, "GPU Memory Total", "D3D Dedicated Memory Total");

    private static GpuStats ReadGpu(IHardware gpu)
    {
        var fan = gpu.Sensors.Where(s => s.SensorType == SensorType.Fan && s.Value.HasValue)
                             .Select(s => (double?)s.Value).Max();
        var power = Find(gpu, SensorType.Power, "GPU Package", "GPU Power", "GPU Core")
                    ?? gpu.Sensors.FirstOrDefault(s => s.SensorType == SensorType.Power && s.Value.HasValue)?.Value;
        var vramUsed = Find(gpu, SensorType.SmallData, "GPU Memory Used", "D3D Dedicated Memory Used");
        var vramTotal = Find(gpu, SensorType.SmallData, "GPU Memory Total", "D3D Dedicated Memory Total");

        return new GpuStats(
            gpu.Name,
            Round(Find(gpu, SensorType.Load, "GPU Core", "D3D 3D"), 1),
            Round(Find(gpu, SensorType.Temperature, "GPU Core", "GPU VR SoC", "GPU Package"), 1),
            Round(Find(gpu, SensorType.Temperature, "GPU Hot Spot"), 1),
            Round(Find(gpu, SensorType.Clock, "GPU Core")),
            Round(Find(gpu, SensorType.Clock, "GPU Memory")),
            Round(fan),
            Round(power, 1),
            Round(vramUsed),
            Round(vramTotal));
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MemoryStatusEx
    {
        public uint dwLength, dwMemoryLoad;
        public ulong ullTotalPhys, ullAvailPhys, ullTotalPageFile, ullAvailPageFile,
                     ullTotalVirtual, ullAvailVirtual, ullAvailExtendedVirtual;
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool GlobalMemoryStatusEx(ref MemoryStatusEx buffer);

    private static RamStats ReadRam()
    {
        var m = new MemoryStatusEx { dwLength = (uint)Marshal.SizeOf<MemoryStatusEx>() };
        if (!GlobalMemoryStatusEx(ref m)) return new RamStats(0, 0, 0);
        const double gb = 1024d * 1024 * 1024;
        var total = m.ullTotalPhys / gb;
        var used = (m.ullTotalPhys - m.ullAvailPhys) / gb;
        return new RamStats(Math.Round(used, 1), Math.Round(total, 1), Math.Round(used / total * 100, 1));
    }

    private NetStats ReadNet()
    {
        long rx = 0, tx = 0;
        foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (nic.OperationalStatus != OperationalStatus.Up) continue;
            if (nic.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel) continue;
            // Hyper-V / WSL virtual switches mirror the physical adapter's traffic.
            if (nic.Description.Contains("Hyper-V", StringComparison.OrdinalIgnoreCase)) continue;
            try
            {
                var s = nic.GetIPStatistics();
                rx += s.BytesReceived;
                tx += s.BytesSent;
            }
            catch (NetworkInformationException) { }
        }

        var now = _clock.Elapsed.TotalSeconds;
        var dt = now - _lastNetT;
        var first = _lastNetT == 0;
        var result = first || dt <= 0
            ? new NetStats(0, 0)
            : new NetStats(Math.Max(0, (rx - _lastRx) / dt), Math.Max(0, (tx - _lastTx) / dt));
        _lastRx = rx; _lastTx = tx; _lastNetT = now;
        return new NetStats(Math.Round(result.Down), Math.Round(result.Up));
    }

    private IReadOnlyList<DriveStats> ReadDrives()
    {
        // Free space barely moves; no need to hit the disks every second.
        var now = _clock.Elapsed.TotalSeconds;
        if (now - _lastDriveT < 10) return _drives;
        _lastDriveT = now;
        _drives = DriveInfo.GetDrives()
            .Where(d => d.DriveType == DriveType.Fixed && d.IsReady)
            .Select(d => new DriveStats(d.Name, SafeLabel(d), d.TotalSize - d.TotalFreeSpace, d.TotalSize))
            .ToArray();
        return _drives;
    }

    private static string SafeLabel(DriveInfo d)
    {
        try { return d.VolumeLabel; } catch { return ""; }
    }

    public void Dispose()
    {
        _computer.Close();
    }
}

/// <summary>Per-process CPU% and RAM, grouped by executable name (so all chrome.exe processes add up).</summary>
internal sealed class ProcessSampler
{
    private readonly Dictionary<int, TimeSpan> _lastCpu = new();
    private readonly Stopwatch _clock = Stopwatch.StartNew();
    private double _lastT;

    public IReadOnlyList<ProcStats> Sample(IReadOnlyDictionary<int, double> gpuByPid)
    {
        var now = _clock.Elapsed.TotalSeconds;
        var dt = now - _lastT;
        _lastT = now;
        var cores = Environment.ProcessorCount;

        var groups = new Dictionary<string, (double cpu, double gpu, long ram)>(StringComparer.OrdinalIgnoreCase);
        var seen = new HashSet<int>();

        foreach (var p in Process.GetProcesses())
        {
            using (p)
            {
                if (p.Id == 0) continue; // System Idle Process
                double cpu = 0;
                long ram;
                try
                {
                    ram = p.WorkingSet64;
                    var t = p.TotalProcessorTime;
                    seen.Add(p.Id);
                    if (_lastCpu.TryGetValue(p.Id, out var prev) && dt > 0)
                        cpu = (t - prev).TotalSeconds / (dt * cores) * 100;
                    _lastCpu[p.Id] = t;
                }
                catch
                {
                    continue; // protected / exited process
                }

                gpuByPid.TryGetValue(p.Id, out var gpu);
                var name = p.ProcessName;
                groups.TryGetValue(name, out var g);
                groups[name] = (g.cpu + cpu, g.gpu + gpu, g.ram + ram);
            }
        }

        foreach (var pid in _lastCpu.Keys.Where(k => !seen.Contains(k)).ToList()) _lastCpu.Remove(pid);

        var list = groups.Select(kv => new ProcStats(kv.Key,
                Math.Round(Math.Clamp(kv.Value.cpu, 0, 100), 1),
                Math.Round(Math.Clamp(kv.Value.gpu, 0, 100), 1),
                kv.Value.ram))
            .ToList();

        // Send the top entries for each column so the phone can re-sort locally.
        return list.OrderByDescending(p => p.Cpu).Take(15)
            .Concat(list.OrderByDescending(p => p.Gpu).Take(15))
            .Concat(list.OrderByDescending(p => p.Ram).Take(15))
            .DistinctBy(p => p.Name)
            .ToList();
    }
}

/// <summary>Per-process GPU% from the "GPU Engine" performance counters, same source Task Manager uses.</summary>
internal sealed class GpuProcessSampler
{
    private PerformanceCounterCategory? _category;
    private Dictionary<string, CounterSample> _last = new();
    private Dictionary<int, double> _result = new();
    private readonly Stopwatch _clock = Stopwatch.StartNew();
    private double _lastT = -100;
    private bool _broken;

    public IReadOnlyDictionary<int, double> Sample()
    {
        if (_broken) return _result;
        var now = _clock.Elapsed.TotalSeconds;
        if (now - _lastT < 2) return _result; // reading this category is fairly expensive
        _lastT = now;

        try
        {
            _category ??= new PerformanceCounterCategory("GPU Engine");
            var data = _category.ReadCategory()["Utilization Percentage"];
            if (data == null) return _result;

            var current = new Dictionary<string, CounterSample>();
            // pid -> engine type -> summed utilisation; Task Manager shows the busiest engine.
            var perPid = new Dictionary<int, Dictionary<string, double>>();

            foreach (InstanceData inst in data.Values)
            {
                var name = inst.InstanceName;
                current[name] = inst.Sample;
                if (!_last.TryGetValue(name, out var prev)) continue;

                var pid = ParsePid(name);
                if (pid <= 0) continue;
                var engine = ParseEngine(name);
                var value = CounterSample.Calculate(prev, inst.Sample);

                if (!perPid.TryGetValue(pid, out var engines)) perPid[pid] = engines = new();
                engines[engine] = engines.GetValueOrDefault(engine) + value;
            }

            _last = current;
            _result = perPid.ToDictionary(kv => kv.Key, kv => kv.Value.Values.Max());
        }
        catch
        {
            _broken = true; // counters disabled or unavailable on this machine
        }
        return _result;
    }

    // Instance names look like: pid_1234_luid_0x00000000_0x0000C6F1_phys_0_eng_0_engtype_3D
    private static int ParsePid(string name)
    {
        if (!name.StartsWith("pid_")) return -1;
        var end = name.IndexOf('_', 4);
        return end > 4 && int.TryParse(name.AsSpan(4, end - 4), out var pid) ? pid : -1;
    }

    private static string ParseEngine(string name)
    {
        var i = name.IndexOf("engtype_", StringComparison.Ordinal);
        return i < 0 ? "" : name[(i + 8)..];
    }
}
