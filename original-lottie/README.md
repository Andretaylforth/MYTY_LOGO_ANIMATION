# MYTY logo animation, original as Lottie

A frame-for-frame vector copy of `Logo Anim 30018-0090-1.mp4` (24 fps, 73 frames, 3.04 s).
Every video frame was traced into bezier curves, and each frame's colour was sampled from the video.

| File | Canvas | Background |
|---|---|---|
| `myty-logo-original.json` | 1920×1080, same framing as the MP4 | Blue `#002A8C` |
| `myty-logo-original-transparent.json` | 1920×1080 | None |
| `myty-logo-original-cropped.json` | 185×435, trimmed to where the animation happens | None (for placing on your own blue) |

Size: about 110 KB each, and about 20 KB when your web server gzips it (most do by default for `.json`).

## Use on a website

```html
<div id="logo" style="width: 100%; max-width: 600px; aspect-ratio: 16 / 9;"></div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/lottie-web/5.12.2/lottie_light.min.js"></script>
<script>
  lottie.loadAnimation({
    container: document.getElementById('logo'),
    renderer: 'svg',          // vector: stays sharp at any size
    loop: false,              // true to repeat
    autoplay: true,
    path: 'myty-logo-original.json'
  });
</script>
```

The animation scales to fill the container, so set the container's size and it stays crisp.
Open `preview.html` through a local server (`python3 -m http.server`) to see all three versions.

## Regenerating

`trace.py` rebuilds the files from the MP4's frames:

```sh
ffmpeg -i "../Logo Anim 30018-0090-1.mp4" frames/f_%03d.png
pip install potracer opencv-python-headless numpy
python3 trace.py frames
```
