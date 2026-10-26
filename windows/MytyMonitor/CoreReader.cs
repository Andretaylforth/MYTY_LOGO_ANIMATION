using System.Text.RegularExpressions;
using LibreHardwareMonitor.Hardware;

namespace MytyMonitor;

/// <summary>
/// Turns LibreHardwareMonitor's CPU sensors into one entry per physical core.
/// Sensors aren't named consistently: Ryzen clocks use the hardware core id ("Core #0".."Core #5", "Core #8"..)
/// while loads count cores from 1 and come per thread ("CPU Core #3 Thread #2"). So nothing is matched by
/// number: the core count comes from Windows (or the thread grouping), and each kind of sensor is lined up
/// with the cores in the order it's listed, folding per-thread entries into their core.
/// </summary>
public static class CoreReader
{
    // "Core #3", "CPU Core #3", "P-Core #1", "E-Core #2" with nothing after (not "(Effective)", "VID", "Distance to TjMax").
    private static readonly Regex CoreSensor = new(@"^(?:CPU )?(?:[PE]-)?Core #\d+$", RegexOptions.IgnoreCase);
    private static readonly Regex LoadSensor = new(@"^CPU Core #(\d+)(?: Thread #\d+)?$", RegexOptions.IgnoreCase);

    public static List<CoreStats> Read(IEnumerable<(string Name, SensorType Type, double? Value)> sensors, int? physicalCores)
    {
        var list = sensors.Where(s => s.Value.HasValue).ToList();

        // Loads, grouped by the core each thread belongs to (this grouping follows the CPU's real topology).
        var loads = new SortedDictionary<int, List<double>>();
        foreach (var s in list.Where(s => s.Type == SensorType.Load))
        {
            var m = LoadSensor.Match(s.Name);
            if (!m.Success) continue;
            var core = int.Parse(m.Groups[1].Value);
            if (!loads.TryGetValue(core, out var l)) loads[core] = l = new List<double>();
            l.Add(s.Value!.Value);
        }
        var clocks = list.Where(s => s.Type == SensorType.Clock && CoreSensor.IsMatch(s.Name)).Select(s => s.Value!.Value).ToList();
        var temps = list.Where(s => s.Type == SensorType.Temperature && CoreSensor.IsMatch(s.Name)).Select(s => s.Value!.Value).ToList();

        var n = physicalCores ?? (loads.Count > 0 ? loads.Count : clocks.Count);
        if (n <= 0) return [];

        var load = Fit(loads.Values.Select(v => v.Average()).ToList(), n, average: true);
        var clock = Fit(clocks, n, average: false);
        var temp = Fit(temps, n, average: false);
        return Enumerable.Range(0, n).Select(i => new CoreStats(
            (i + 1).ToString(),
            temp[i] is { } t ? Math.Round(t, 1) : null,
            clock[i] is { } c ? Math.Round(c) : null,
            load[i] is { } l ? Math.Round(l, 1) : null)).ToList();
    }

    /// <summary>
    /// Lines [values] up with [n] cores. Exactly n: one each. A multiple of n (one per thread): each core
    /// takes its group (max for clocks/temps, average for load). Anything else can't be matched safely: none.
    /// </summary>
    private static List<double?> Fit(List<double> values, int n, bool average)
    {
        if (values.Count == n) return values.Select(v => (double?)v).ToList();
        if (values.Count > n && values.Count % n == 0)
        {
            var per = values.Count / n;
            return Enumerable.Range(0, n)
                .Select(i => values.Skip(i * per).Take(per))
                .Select(g => (double?)(average ? g.Average() : g.Max()))
                .ToList();
        }
        return Enumerable.Repeat<double?>(null, n).ToList();
    }
}
