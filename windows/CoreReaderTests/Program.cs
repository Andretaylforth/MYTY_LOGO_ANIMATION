using LibreHardwareMonitor.Hardware;
using MytyMonitor;
var fails = 0;
void Check(string name, bool ok) { Console.WriteLine($"{(ok ? "PASS" : "FAIL")} {name}"); if (!ok) fails++; }
var L = SensorType.Load; var C = SensorType.Clock; var T = SensorType.Temperature; var F = SensorType.Factor; var P = SensorType.Power;

// Ryzen 9 9900X3D: 12 cores / 24 threads; clocks named by hardware core id (gap between CCDs).
var ryzen = new List<(string, SensorType, double?)> { ("CPU Total", L, 30), ("CPU Core Max", L, 90), ("Core (Tctl/Tdie)", T, 70), ("CCD1 (Tdie)", T, 71), ("CCD2 (Tdie)", T, 64), ("Cores (Average)", C, 4500), ("Bus Speed", C, 100) };
int[] ids = [0, 1, 2, 3, 4, 5, 8, 9, 10, 11, 12, 13];
foreach (var (id, i) in ids.Select((id, i) => (id, i)))
{
    ryzen.Add(($"Core #{id}", C, 5000 + i)); ryzen.Add(($"Core #{id} (Effective)", C, 3000)); ryzen.Add(($"Core #{id}", F, 50));
    ryzen.Add(($"Core #{id} (SMU)", P, 5)); ryzen.Add(($"Core #{id} VID", SensorType.Voltage, 1.2));
}
for (var k = 1; k <= 12; k++) { ryzen.Add(($"CPU Core #{k} Thread #1", L, 10 * k)); ryzen.Add(($"CPU Core #{k} Thread #2", L, 10 * k + 10)); }
foreach (int? n in new int?[] { 12, null })
{
    var r = CoreReader.Read(ryzen, n);
    Check($"ryzen ({(n == null ? "no WMI" : "WMI")}): 12 cores", r.Count == 12);
    Check("ryzen: clocks in order", r.Select(c => c.Clock).SequenceEqual(Enumerable.Range(0, 12).Select(i => (double?)(5000 + i))));
    Check("ryzen: load is thread average", r[0].Load == 15 && r[11].Load == 125);
    Check("ryzen: no per-core temps", r.All(c => c.Temp == null));
}

// Intel i7 8 cores / 16 threads.
var intel = new List<(string, SensorType, double?)> { ("CPU Total", L, 20), ("Core Max", T, 80), ("Core Average", T, 60), ("CPU Package", T, 75) };
for (var k = 1; k <= 8; k++)
{
    intel.Add(($"CPU Core #{k}", T, 50 + k)); intel.Add(($"CPU Core #{k} Distance to TjMax", T, 40));
    intel.Add(($"CPU Core #{k}", C, 4000 + k)); intel.Add(($"CPU Core #{k}", SensorType.Voltage, 1.1));
    intel.Add(($"CPU Core #{k} Thread #1", L, 10)); intel.Add(($"CPU Core #{k} Thread #2", L, 30));
}
var ir = CoreReader.Read(intel, 8);
Check("intel: 8 cores with temps", ir.Count == 8 && ir.Select(c => c.Temp).SequenceEqual(Enumerable.Range(1, 8).Select(k => (double?)(50 + k))));
Check("intel: load averaged", ir.All(c => c.Load == 20));

// Intel hybrid: 6 P-cores (2 threads) + 8 E-cores (1 thread) = 14 cores / 20 threads.
var hybrid = new List<(string, SensorType, double?)>();
for (var k = 1; k <= 6; k++) { hybrid.Add(($"P-Core #{k}", T, 70)); hybrid.Add(($"P-Core #{k}", C, 5500)); }
for (var k = 1; k <= 8; k++) { hybrid.Add(($"E-Core #{k}", T, 55)); hybrid.Add(($"E-Core #{k}", C, 4300)); }
for (var k = 1; k <= 6; k++) { hybrid.Add(($"CPU Core #{k} Thread #1", L, 50)); hybrid.Add(($"CPU Core #{k} Thread #2", L, 50)); }
for (var k = 7; k <= 14; k++) hybrid.Add(($"CPU Core #{k}", L, 25));
var hr = CoreReader.Read(hybrid, 14);
Check("hybrid: 14 cores", hr.Count == 14);
Check("hybrid: P then E", hr[0].Temp == 70 && hr[13].Temp == 55 && hr[13].Clock == 4300 && hr[13].Load == 25);
Check("hybrid without WMI: 14 cores", CoreReader.Read(hybrid, null).Count == 14);

// One clock sensor per thread (24 for 12 cores): folded to 12.
var perThread = Enumerable.Range(0, 24).Select(i => ($"Core #{i}", C, (double?)(4000 + i))).ToList();
var pt = CoreReader.Read(perThread, 12);
Check("per-thread clocks: 12 cores", pt.Count == 12 && pt[0].Clock == 4001 && pt[11].Clock == 4023);
Console.WriteLine(fails == 0 ? "ALL PASS" : $"{fails} FAILED");
return fails;
