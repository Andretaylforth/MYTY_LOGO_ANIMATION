# MYTY boot sequence

`../myty-boot-sequence.mp4` is a 31 s, 1920x1080, 30 fps boot sequence in the MYTY style:

1. CRT power-on
2. 90s BIOS banner: MYTY mark, "Modular Power Ally" badge, memory test, IDE detection
3. System Configurations grid and PCI device listing, with rounded double borders
4. Blue boot screen with the live site's logo animation (`logo.json`, "ascii v2"), a pixel spinner, then the logo squashes, jumps and grows into the lock screen
5. Gradient lock screen, then sign-in, then "Welcome"

Everything is drawn in `boot.html` as a function of time. Open it in a browser (via a local server) to preview it live.
Re-render the video with:

    node render.cjs ../myty-boot-sequence.mp4

Timings live in the `T` object at the top of `boot.html`; colours, fonts (Px437, VCR OSD Mono) and text are in the scene functions.
