# AsciiWordCycle (Framer code component)

Replaces the static "portable / powerful / modular." headline. Each line cycles through its own
three words on a random 3–5 s timer. On a change the old word dissolves into ASCII glyphs and the
new word is typed in letter by letter, each letter flickering through ASCII characters first.

Editable in Framer's properties panel: the words for each line, the full stop after the last line,
font, colour, size and phone size, line height, min/max wait, typing speed, flicker amount,
glyph set and alignment. It pauses off-screen and respects reduced-motion settings.

Default words:
- Line 1: portable, open source, travel ready
- Line 2: powerful, desktop class, gaming
- Line 3: modular, rebuildable, repairable

Install without the MCP: in Framer, Assets → Code → New file → "AsciiWordCycle.tsx",
paste this file, then drag the component where the static text is and match Size / Font / Color.
