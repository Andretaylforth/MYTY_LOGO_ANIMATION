# MYTY boot sequence

`../myty-boot-sequence.mp4` is a 16 s, 1920x1080, 30 fps boot sequence in the MYTY style:

1. Black screen
2. Sci-fi terminal: ASCII MYTY mark, tabbed SYSTEM (specs) -> KERNEL (modules loading) -> BOOT (DMI verify, hand-off),
   a SYSTEM CONFIGURATIONS panel with a memory map, and a BUS LOGGER panel with a live plot
3. Blue boot screen with the live site's logo animation (`logo.json`, "ascii v2") and a pixel spinner that starts as the logo finishes
4. The logo jumps up and slams back to the centre; the time and date come in as it lands
5. End screen: time and date on the logo blue, with a thin grid and terminal framing

Everything is drawn in `boot.html` as a function of time. Open it in a browser (via a local server) to preview it live.
Re-render the video with:

    node render.cjs ../myty-boot-sequence.mp4

Timings live in the `T` object at the top of `boot.html`; colours, fonts (Px437, VCR OSD Mono) and text are in the scene functions.
