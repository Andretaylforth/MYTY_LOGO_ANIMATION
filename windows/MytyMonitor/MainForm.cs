using System.Drawing.Drawing2D;

namespace MytyMonitor;

internal static class Theme
{
    public static readonly Color MytyBlue = Color.FromArgb(0, 42, 140);
    public static readonly Color Bg = Color.FromArgb(18, 19, 22);
    public static readonly Color Card = Color.FromArgb(29, 30, 34);
    public static readonly Color Border = Color.FromArgb(44, 46, 52);
    public static readonly Color Text = Color.FromArgb(232, 233, 238);
    public static readonly Color Dim = Color.FromArgb(138, 141, 150);
    public static readonly Color Good = Color.FromArgb(77, 124, 255);

    public static GraphicsPath RoundRect(RectangleF r, float radius)
    {
        var d = radius * 2;
        var p = new GraphicsPath();
        p.AddArc(r.X, r.Y, d, d, 180, 90);
        p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
        p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
        p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
        p.CloseFigure();
        return p;
    }

    /// <summary>The Myty mark: a rounded square with four round holes.</summary>
    public static void DrawLogo(Graphics g, RectangleF r, Color fg, Color holes)
    {
        g.SmoothingMode = SmoothingMode.AntiAlias;
        using (var path = RoundRect(r, r.Width * 0.19f))
        using (var b = new SolidBrush(fg))
            g.FillPath(b, path);
        var rad = r.Width * 0.19f;
        var off = r.Width * 0.27f;
        using var hb = new SolidBrush(holes);
        foreach (var cx in new[] { r.X + off, r.Right - off })
        foreach (var cy in new[] { r.Y + off, r.Bottom - off })
            g.FillEllipse(hb, cx - rad, cy - rad, rad * 2, rad * 2);
    }
}

/// <summary>Bento-style rounded card.</summary>
internal sealed class Card : Panel
{
    public string Title { get; set; } = "";

    public Card()
    {
        DoubleBuffered = true;
        BackColor = Theme.Bg;
        Padding = new Padding(16, 40, 16, 12);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        var r = new RectangleF(0.5f, 0.5f, Width - 1.5f, Height - 1.5f);
        using var path = Theme.RoundRect(r, 10);
        using var fill = new SolidBrush(Theme.Card);
        using var pen = new Pen(Theme.Border);
        g.FillPath(fill, path);
        g.DrawPath(pen, path);
        using var font = new Font("Segoe UI Semibold", 11f);
        TextRenderer.DrawText(g, Title, font, new Point(14, 12), Theme.Text);
    }
}

public sealed class MainForm : Form
{
    private readonly StatsServer _server;
    private readonly AdbBridge _adb;
    private readonly Settings _settings;
    private readonly NotifyIcon _tray;
    private readonly System.Windows.Forms.Timer _timer = new() { Interval = 1000 };
    private bool _exiting;

    private readonly Label _addresses = MakeLabel(14f, Theme.Text);
    private readonly Label _wifiHint = MakeLabel(9f, Theme.Dim);
    private readonly Label _usb = MakeLabel(10f, Theme.Text);
    private readonly Label _usbHint = MakeLabel(9f, Theme.Dim);
    private readonly Label _live = MakeLabel(10.5f, Theme.Text);
    private readonly Label _clients = MakeLabel(9f, Theme.Dim);

    public MainForm(StatsServer server, AdbBridge adb, Settings settings)
    {
        _server = server;
        _adb = adb;
        _settings = settings;

        Text = "Myty Monitor";
        Icon = LoadIcon();
        BackColor = Theme.Bg;
        ForeColor = Theme.Text;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        AutoScaleMode = AutoScaleMode.Dpi;
        ClientSize = new Size(440, 610);
        Font = new Font("Segoe UI", 9f);

        var header = new Panel { Dock = DockStyle.Top, Height = 96, BackColor = Theme.MytyBlue };
        header.Paint += (_, e) =>
        {
            var s = header.Height * 0.5f;
            Theme.DrawLogo(e.Graphics, new RectangleF(24, (header.Height - s) / 2, s, s),
                Color.FromArgb(240, 240, 240), Theme.MytyBlue);
            using var big = new Font("Segoe UI Semibold", 17f);
            using var small = new Font("Segoe UI", 9.5f);
            TextRenderer.DrawText(e.Graphics, "Myty Monitor", big, new Point((int)(36 + s), 22), Color.White);
            TextRenderer.DrawText(e.Graphics, "Live PC stats on your phone", small,
                new Point((int)(38 + s), 56), Color.FromArgb(190, 205, 255));
        };

        var body = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            Padding = new Padding(16, 14, 16, 8),
            BackColor = Theme.Bg,
        };

        body.Controls.Add(MakeCard("Wi-Fi", 128, _addresses, _wifiHint));
        body.Controls.Add(MakeCard("USB cable", 104, _usb, _usbHint));
        body.Controls.Add(MakeCard("Live", 112, _live, _clients));

        var startup = MakeCheck("Start with Windows", SystemSetup.IsStartupEnabled());
        startup.CheckedChanged += (_, _) => SystemSetup.SetStartup(startup.Checked);
        var minimized = MakeCheck("Start hidden in the system tray", _settings.StartMinimized);
        minimized.CheckedChanged += (_, _) => { _settings.StartMinimized = minimized.Checked; _settings.Save(); };
        body.Controls.Add(startup);
        body.Controls.Add(minimized);

        Controls.Add(body);
        Controls.Add(header);

        _wifiHint.Text = "The phone finds this PC automatically on the same Wi-Fi. If not, type the\n" +
                         "first address into the app. Test: open http://<address> in the phone's browser.";
        _usbHint.Text = "Turn on USB debugging on the phone and plug it in, or turn on USB tethering\nand use Wi-Fi mode. See README for details.";

        _tray = new NotifyIcon
        {
            Icon = Icon,
            Text = "Myty Monitor",
            Visible = true,
            ContextMenuStrip = new ContextMenuStrip(),
        };
        _tray.ContextMenuStrip.Items.Add("Open", null, (_, _) => ShowFromTray());
        _tray.ContextMenuStrip.Items.Add("Exit", null, (_, _) => { _exiting = true; Close(); });
        _tray.DoubleClick += (_, _) => ShowFromTray();

        _timer.Tick += (_, _) => Refresh_();
        _timer.Start();
        Refresh_();
    }

    private static Icon LoadIcon()
    {
        using var s = typeof(MainForm).Assembly.GetManifestResourceStream("MytyMonitor.myty.ico");
        return s != null ? new Icon(s) : SystemIcons.Application;
    }

    private static Label MakeLabel(float size, Color color) => new()
    {
        AutoSize = true,
        Font = new Font("Segoe UI", size),
        ForeColor = color,
        BackColor = Theme.Card,
        Margin = new Padding(0, 0, 0, 6),
    };

    private static CheckBox MakeCheck(string text, bool value) => new()
    {
        Text = text,
        Checked = value,
        AutoSize = true,
        ForeColor = Theme.Text,
        Margin = new Padding(4, 6, 0, 0),
    };

    private static Card MakeCard(string title, int height, params Control[] lines)
    {
        var card = new Card { Title = title, Width = 408, Height = height, Margin = new Padding(0, 0, 0, 12) };
        var flow = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            BackColor = Theme.Card,
        };
        flow.Controls.AddRange(lines);
        card.Controls.Add(flow);
        return card;
    }

    private void Refresh_()
    {
        var ips = Discovery.LocalIPv4().Select(a => a.ip.ToString()).Distinct().ToList();
        _addresses.Text = !_server.LanEnabled ? "Wi-Fi blocked: run as administrator"
                        : ips.Count == 0 ? "No network connection"
                        : $"{ips[0]}   (port {_server.Port})" +
                          (ips.Count > 1 ? $"\nother adapters: {string.Join(", ", ips.Skip(1).Take(3))}" : "");

        _usb.Text = _adb.Status;

        var s = _server.Latest;
        _live.Text = s == null ? "Reading sensors…"
            : $"CPU  {Fmt(s.Cpu.Temp, "°C")}   {Fmt(s.Cpu.Load, "%")}        " +
              $"GPU  {Fmt(s.Gpu?.Temp, "°C")}   {Fmt(s.Gpu?.Load, "%")}";

        if (s != null && s.Gpus.Count > 1)
        {
            var others = string.Join(", ", s.Gpus.Skip(1).Select(g => g.Integrated ? $"{g.Name} (integrated)" : g.Name));
            _live.Text += $"\nGPU: {s.Gpus[0].Name}   ·   ignoring {others}";
        }

        var clients = _server.ActiveClients();
        _clients.Text = clients.Count == 0 ? "No phone connected yet"
            : $"Streaming to {clients.Count} phone{(clients.Count == 1 ? "" : "s")}: " +
              string.Join(", ", clients.Select(c => c == "127.0.0.1" ? "USB" : c));
        _tray.Text = s == null ? "Myty Monitor" : $"Myty Monitor · CPU {Fmt(s.Cpu.Temp, "°")} · GPU {Fmt(s.Gpu?.Temp, "°")}";
    }

    private static string Fmt(double? v, string unit) => v.HasValue ? $"{v.Value:0}{unit}" : "--";

    private void ShowFromTray()
    {
        Show();
        WindowState = FormWindowState.Normal;
        Activate();
    }

    protected override void SetVisibleCore(bool value)
    {
        // Honour "start hidden" without flashing the window.
        if (!IsHandleCreated && (_settings.StartMinimized || Program.StartHidden))
        {
            CreateHandle();
            value = false;
        }
        base.SetVisibleCore(value);
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        if (!_exiting && e.CloseReason == CloseReason.UserClosing)
        {
            // Closing the window keeps streaming from the tray; use the tray menu to quit.
            e.Cancel = true;
            Hide();
            _tray.ShowBalloonTip(2000, "Myty Monitor", "Still running in the tray. Right-click the icon to exit.", ToolTipIcon.None);
            return;
        }
        _tray.Visible = false;
        _tray.Dispose();
        base.OnFormClosing(e);
    }
}
