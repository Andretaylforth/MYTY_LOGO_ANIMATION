namespace MytyMonitor;

// Wire format sent to the phone. Keep in sync with android/app/src/main/java/com/myty/monitor/data/Stats.kt.
// Any value the PC can't read is sent as null and shown as "--" on the phone.

public sealed record CpuStats(
    string Name,
    double? Load,     // %
    double? Temp,     // °C
    double? Clock,    // MHz (fastest core)
    double? Fan,      // RPM
    double? Power,    // W
    IReadOnlyList<CoreStats> Cores);

/// <summary>
/// One CPU core. Ryzens don't report a temperature per core, only per CCD (chiplet); there [Temp] is the
/// temperature of the core's CCD and [TempSource] is "ccd" (or "cpu" for a single whole-chip reading).
/// </summary>
public sealed record CoreStats(string Name, double? Temp, double? Clock, double? Load, string? TempSource = "core"); // °C, MHz, %

public sealed record GpuStats(
    string Name,
    double? Load,     // %
    double? Temp,     // °C
    double? Hotspot,  // °C
    double? Clock,    // MHz
    double? MemClock, // MHz
    double? Fan,      // RPM
    double? Power,    // W
    double? VramUsed, // MB
    double? VramTotal);

public sealed record RamStats(double Used, double Total, double Load); // GB, GB, %

public sealed record NetStats(double Down, double Up); // bytes/s

public sealed record DriveStats(string Name, string Label, long Used, long Total); // bytes

public sealed record GpuInfo(string Name, bool Integrated);

public sealed record ProcStats(string Name, double Cpu, double Gpu, long Ram); // %, %, bytes

public sealed record Snapshot(
    int V,
    string Host,
    long Time,
    CpuStats Cpu,
    GpuStats? Gpu,            // the dedicated card when there is one
    IReadOnlyList<GpuInfo> Gpus, // every GPU found, best first
    RamStats Ram,
    NetStats Net,
    IReadOnlyList<DriveStats> Drives,
    IReadOnlyList<ProcStats> Procs);
