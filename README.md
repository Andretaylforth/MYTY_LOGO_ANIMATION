# Myty Monitor

Live PC stats (CPU and GPU temperatures, load, clocks, fans, RAM, network, storage, top processes) shown fullscreen on your phone in the Myty bento style.

![Dashboard](docs/dashboard.png)

It has two parts:

| Folder | What it is |
| --- | --- |
| `windows/` | **Myty Monitor for Windows**. A small tray app that reads the PC's sensors every second and serves them on port 5317. |
| `android/` | **Myty Monitor for Android**. A fullscreen dashboard that connects to the PC over Wi-Fi or a USB cable. |

## Getting the apps

Every push runs **GitHub Actions → Build apps**, which uploads two downloads:

- `MytyMonitor-windows` contains `MytyMonitor.exe` (a single file; .NET doesn't need to be installed)
- `MytyMonitor-android` contains `app-release.apk`

To build them yourself:

```powershell
# Windows app (needs the .NET 8 SDK)
dotnet publish windows/MytyMonitor/MytyMonitor.csproj -c Release -o out
```

```bash
# Android app (needs JDK 17 and the Android SDK, or just open android/ in Android Studio)
cd android && ./gradlew assembleRelease
# -> android/app/build/outputs/apk/release/app-release.apk
```

## Using it

1. **On the PC**, run `MytyMonitor.exe`. It asks for admin rights, because Windows only lets admin programs read CPU temperatures and fan speeds. The window shows the PC's address and whether a phone is connected. Closing the window keeps it running in the tray; right-click the tray icon to exit. Tick **Start with Windows** to launch it at login with no UAC prompt.
2. **On the phone**, open Myty Monitor and tap your PC. It reconnects to the same PC automatically next time.

### Wi-Fi

Put the phone and PC on the same network. The PC announces itself over UDP port 5318, so it shows up in the app's list. If it doesn't (some routers block this, for example guest networks or "AP isolation"), type the address shown in the PC window, such as `192.168.1.20`.

The first time it runs, the PC app opens TCP port 5317 (stats) and UDP port 5318 (discovery) in Windows Firewall. Other antivirus or firewall software may need the same two ports allowed.

**Can't connect?** In the phone's browser, open `http://<PC address>:5317` (for example `http://192.168.1.20:5317`).
- If the page says *"Myty Monitor is running"*, the network is fine; check the address typed into the app.
- If it doesn't load, something between the phone and PC is blocking it. Make sure both are on the same Wi-Fi (not a guest network, and not mobile data on the phone), and check any third-party firewall on the PC.

The address in the PC window, e.g. `192.168.1.20 (port 5317)`, is all you need to type. Adding `:5317` on the end is optional. Addresses listed under "other adapters" belong to virtual adapters (VPN, WSL, Hyper-V) and won't work from the phone.

### USB cable

There are two ways to connect over a cable:

- **USB debugging (recommended):** turn on Developer options on the phone (tap *Build number* 7 times in *Settings → About phone*), turn on **USB debugging**, then plug the phone in and accept the prompt. The PC app runs `adb reverse` for you, and the phone shows the PC as **USB**. This needs `adb.exe`: either put [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) next to `MytyMonitor.exe` (as `adb.exe` or in a `platform-tools` folder), or have it on your PATH. If you have Android Studio installed it's found automatically.
- **USB tethering (no developer options):** plug in the phone and turn on *Settings → Network → Hotspot & tethering → USB tethering*. The PC now shares a network with the phone over the cable, and it shows up in the list like it does on Wi-Fi.

## The dashboard

- **CPU / GPU**: ring gauge, plus bars for temperature, clock and fan. Tap a card, or use the `•••` menu, to switch the gauges between **load** and **temperature**. Temperature colours go from blue to amber to red as the part heats up. The GPU card also shows hot-spot temperature, power and VRAM when the card reports them.
- **RAM, Network** (with a 60-second graph), **Storage** (every fixed drive), and **Top Processes**. Tap CPU / GPU / RAM in the table header to sort.
- Works in landscape (as in the screenshot) and portrait, and scales to fit any phone or tablet. The screen stays on, and the layout shifts by a couple of pixels each minute to avoid OLED burn-in.
- Press Back to disconnect and choose a different PC.

## Notes and limits

- Sensors are read with [LibreHardwareMonitor](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor). Anything a sensor doesn't report shows as `--`. If CPU temperature or fans show `--` even when running as admin, the hardware driver probably isn't installed: recent LibreHardwareMonitor versions use **PawnIO**. Installing PawnIO (the LibreHardwareMonitor app offers to do it) normally fixes this.
- **CPU fan** is the first motherboard fan header named "CPU" or "Pump", or else the first one spinning. Many motherboards label headers only as "Fan #1, #2…", so on those boards the reading may come from a different fan.
- **Per-process GPU %** uses the same Windows counters as Task Manager. Per-process network usage isn't shown, because Windows has no cheap way to read it.
- The PC app serves read-only stats over plain HTTP on whatever network the PC is on (including ones Windows marks as Public), so anyone on the same network can read them, including process names. Quit it from the tray on networks you don't trust.
- The release APK is signed with a debug key so it installs directly. Builds from different machines (or CI runs) use different keys, so uninstall the old app before installing a newer APK from somewhere else. Add your own signing key before publishing to the Play Store.

## Protocol

To build other clients: `GET http://<pc>:5317/api/stats` returns the latest snapshot as JSON. The fields are listed in `windows/MytyMonitor/Models.cs`, and units are noted next to each one. `GET /api/hello` returns `{ "app": "myty-monitor", "name": "<PC name>", "port": 5317 }`. The PC broadcasts that same JSON on UDP 5318 every 2 seconds, and answers a `MYTY?` datagram on that port with it.

`Logo Anim 30018-0090-1.mp4` is the Myty logo animation, and the source of the brand colours used here (`#002A8C` and the four-hole mark).
