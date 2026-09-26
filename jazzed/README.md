# MYTY logo animation, jazzed-up version

A reworked take on `Logo Anim 30018-0090-1.mp4`: 4 s at 60 fps, 1920×1080, loops cleanly.

The triangle drops in and squashes on landing. The two bars whip in from each side and merge, and the triangle lands on the bar and tips it like a seesaw. The bar then squashes and pops into the rounded square, with a shockwave, sparks and a glow. The four holes punch out one by one, the triangle dives back into the logo, and the logo settles with a squash.

| File | What it is | Use it for |
|---|---|---|
| `myty-logo.svg` / `myty-logo-transparent.svg` | Self-contained animated SVG (SMIL), pure vector | Websites: `<img src="myty-logo.svg">`, no JS, scales to any size |
| `myty-logo.json` / `myty-logo-transparent.json` | Lottie (Bodymovin) vector animation | Web/iOS/Android via lottie-web / lottie-ios / lottie-android, LottieFiles, After Effects (LottieFiles plugin) |
| `myty-logo.webm` / `myty-logo-transparent.webm` | VP9 WebM; the transparent one keeps the alpha channel | Web `<video>`, OBS, editors that accept WebM |
| `preview.html` | Plays all three side by side | Open locally (serve the folder, e.g. `python3 -m http.server`) |

All vector files are generated from one timeline in `build.py` (`python3 build.py`). Tweak timings, colors (`BG`, `INK`, `GLOW`) or size there.
