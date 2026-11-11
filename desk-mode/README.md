# MYTY MAX configurator: desk mode

Record of the desk-mode change applied to `ProductConfigurator.tsx` in the Framer project
(the configurator source itself is not kept here).

- `src/config.tsx.txt`: the six views (`DESK_ENVIRONMENTS`): photo, camera, product placement, lights, shadow.
- `src/engine.tsx.txt`: compositing (photo background, camera match and crop, baked shadows, reflections).
- `src/ui.tsx.txt`: camera button icon, environment/view selector, `?deskcal` calibration panel.
- `src/apply.py`: the anchored insertions that wire the three blocks into the component.
- `plates/`: the stand-in photographs, their hosted URLs, and the offline renderer that made them
  (`plategen.html`, `render.js`). They are CG placeholders with exactly known cameras.
- `screenshots/`: results from the local test harness.

To use a real photograph: upload it, put its URL in `DESK_PLATE_URLS`, open the configurator with
`?deskcal`, match the grid to the desk, and paste the copied JSON back into `DESK_ENVIRONMENTS`.
