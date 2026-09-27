# Auslander homepage hero video (compressed)

Sources: `wp-content/uploads/2024/07/Desktop_regular.mp4` (21.8 MB) and `Mobile_regular.mp4` (11.3 MB).
26 s, 24 fps, no audio. The mobile source is stored anamorphic (1920×1080 flagged 9:16), so it is exported at its true 608×1080.

| File | Codec | Size | SSIM vs original | Plays in |
|---|---|---|---|---|
| `hero-desktop-av1.mp4` | AV1 | 5.12 MB | 0.979 | Chrome, Edge, Firefox, Android; Safari on M3 / iPhone 15 Pro and newer |
| `hero-desktop-hevc.mp4` | HEVC (hvc1) | 4.74 MB | 0.972 | All Safari (Mac, iPhone, iPad); Chrome/Edge with hardware HEVC |
| `hero-desktop-h264.mp4` | H.264 | 4.72 MB | 0.962 | Everything, but visibly blocky in dark areas at this size |
| `hero-mobile-av1.mp4` | AV1 | 2.76 MB | 0.978 | as above |
| `hero-mobile-hevc.mp4` | HEVC (hvc1) | 2.54 MB | 0.971 | as above |
| `hero-mobile-h264.mp4` | H.264 | 2.54 MB | 0.964 | as above |

AV1 is the best quality per MB and is visually almost identical to the original. Serve all three, and the browser picks the first one it can play:

```html
<video autoplay muted loop playsinline preload="auto" poster="hero-poster.jpg">
  <source src="hero-desktop-av1.mp4"  type='video/mp4; codecs="av01.0.08M.08"'>
  <source src="hero-desktop-hevc.mp4" type='video/mp4; codecs="hvc1"'>
  <source src="hero-desktop-h264.mp4" type="video/mp4">
</video>
```

Framer's built-in Video component takes a single file, so the three-format fallback needs a small code component (or use the HEVC file alone as a compromise that works on Apple devices and most Chrome installs).
