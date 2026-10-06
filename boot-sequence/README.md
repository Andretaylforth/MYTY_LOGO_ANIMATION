# MYTY boot sequence

`../myty-boot-sequence.mp4` is a 16 s, 1920x1080, 30 fps boot sequence in the MYTY style:

1. Black screen
2. 90s BIOS banner: MYTY mark, animated, dithered 80s "Modular Power Ally" emblem, memory test, IDE detection
3. System Configurations grid and PCI device listing, with rounded double borders
4. Blue boot screen with the live site's logo animation (`logo.json`, "ascii v2") and a pixel spinner that starts as the logo finishes
5. The logo jumps up and slams back to the centre; the time and date come in as it lands
6. End screen: time and date on the logo blue

Everything is drawn in `boot.html` as a function of time. Open it in a browser (via a local server) to preview it live.
Re-render the video with:

    node render.cjs ../myty-boot-sequence.mp4

The BIOS screens play back `BIOS_SPEED` times faster than they are authored. Timings live in the `T` object at the top of `boot.html`; colours, fonts (Px437, VCR OSD Mono) and text are in the scene functions.
