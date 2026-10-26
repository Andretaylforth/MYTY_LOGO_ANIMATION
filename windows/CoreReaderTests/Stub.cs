// Test-only stand-in for LibreHardwareMonitor's enum (the real package only loads on Windows).
namespace LibreHardwareMonitor.Hardware;
public enum SensorType { Voltage, Current, Power, Clock, Temperature, Load, Frequency, Fan, Flow, Control, Level, Factor, Data, SmallData, Throughput, TimeSpan, Energy, Noise, Conductivity, Humidity }
