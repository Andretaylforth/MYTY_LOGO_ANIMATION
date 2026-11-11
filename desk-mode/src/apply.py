import sys, json
base = open('ProductConfigurator.orig.tsx').read()
names = [e + v + t for e in ('env01','env02','env03') for v in 'ab' for t in ('', '-s')] + ['env04a', 'env04a-s']
urls = json.load(open(sys.argv[1])) if len(sys.argv) > 1 else {n: '/plates/' + n + '.jpg' for n in names}
plates = '{\n' + ''.join('    "%s": "%s",\n' % (n, urls[n]) for n in names) + '}'
R = lambda n: open('desk/' + n).read()
ops = []
def op(find, repl, count=1): ops.append((find, repl, count))
A = lambda find, add: op(find, find + add)   # insert after
B = lambda find, add: op(find, add + find)   # insert before

B('const ALU_WHITE = "#e6e6e6"', R('config.txt').replace('__DESK_PLATE_URLS__', plates))
B('function fitZoom(aspect: number): number {', R('engine.txt'))
A('    resetViewKey?: number\n', '    // desk mode: the photographic view to stand the case in (null = normal viewer)\n    desk?: DeskView | null\n')
op('colourSel, wantCasePreviews, preset, partColors,', 'colourSel, wantCasePreviews, preset: scenePreset, partColors,')
op('suspendAutoRotate, apiRef, onPreviews, resetViewKey = 0 } = props\n',
   'suspendAutoRotate, apiRef, onPreviews, resetViewKey = 0, desk = null } = props\n'
   '    // desk mode swaps in the photo\'s own lights, but only once that photo is on screen\n'
   '    const [deskShown, setDeskShown] = useState<DeskView | null>(null)\n'
   '    const preset = useMemo(() => (deskShown ? deskPreset(scenePreset, deskShown) : scenePreset), [scenePreset, deskShown])\n')
op('            scene.add(ambient, key, fill, rim)\n',
   '            // the three directional lights hang off one rig, so desk mode can turn them with the case\n'
   '            const lightRig = new THREE.Group()\n            lightRig.add(key, fill, rim)\n            scene.add(ambient, lightRig)\n')
A('world.filmMat = filmMat; world.drawFrame = drawFrame\n', '            world.lightRig = lightRig\n')
A('                const ctx = world\n', '                if (ctx && ctx.desk && ctx.desk.active) { deskRender(ctx); drawSelectionOutline(ctx); return }\n')
A('                controls.update()\n', '                if (world.desk && world.desk.active) deskCamera(world)\n')
A('            renderer.domElement.addEventListener("click", onClick)\n', '''
            // Desk mode: the camera is locked to the photograph, so a drag turns
            // the case on the desk instead (its shadow re-bakes as it turns).
            let deskDrag: { x: number; yaw: number; id: number } | null = null
            const onDeskDown = (e: PointerEvent) => { const d = world.desk; if (d && d.active && e.button === 0) deskDrag = { x: e.clientX, yaw: d.yaw, id: e.pointerId } }
            const onDeskMove = (e: PointerEvent) => {
                const d = world.desk
                if (!deskDrag || e.pointerId !== deskDrag.id || !d || !d.active) return
                const dx = e.clientX - deskDrag.x
                if (Math.abs(dx) < 6 && d.yaw === deskDrag.yaw) return
                d.yaw = deskDrag.yaw + dx * 0.008
                d.dirtyUntil = performance.now() + 300
            }
            const onDeskUp = () => { deskDrag = null }
            renderer.domElement.addEventListener("pointerdown", onDeskDown)
            window.addEventListener("pointermove", onDeskMove)
            window.addEventListener("pointerup", onDeskUp)
            window.addEventListener("pointercancel", onDeskUp)
            world.deskUnlisten = () => { window.removeEventListener("pointermove", onDeskMove); window.removeEventListener("pointerup", onDeskUp); window.removeEventListener("pointercancel", onDeskUp) }
''')
A('            const world = worldRef.current; if (!world) return\n            world.disposed = true\n', '            world.deskUnlisten?.(); deskDispose(world)\n')
op('        const dur = 900, t0 = performance.now()\n', '        const dur = (preset as any).instant ? 1 : 900, t0 = performance.now()\n')
B('    useEffect(() => {\n        if (!resetViewKey) return\n', '''    // Desk mode: load the view's photograph, then switch photo, camera, lights
    // and shadow together. The case and everything on it are left as they are.
    useEffect(() => {
        const world = worldRef.current
        if (!world || status !== "ready") return
        if (!desk) { deskExit(world); setDeskShown(null); return }
        let stop = false
        deskLoadTexture(world, deskImageSrc(desk)).then((tex) => {
            if (stop || world.disposed) return
            deskApply(world, desk, tex)
            setDeskShown(desk)
        }).catch((err) => { if (!stop) console.warn("Desk photo failed to load:", err) })
        return () => { stop = true }
    }, [desk, status])
    // anything that changes what the case looks like re-bakes its shadow
    useEffect(() => {
        const d = worldRef.current?.desk
        if (d) d.dirtyUntil = performance.now() + 1500
    }, [placedParts, adjusting, isolateUid, csKey, colourSel?.isolate, placementMode, partColors])

''')
B('// Lighting: a small sun -- cycles the lighting scenes.\nfunction LightIcon() {', R('ui.txt'))
A('    const suspendAutoRotate = panel === "parts" || !!placementMode || !!adjusting\n', '''
    // Desk mode: the case shown inside a photograph. Purely a presentation
    // layer -- it reads the configuration but never changes it.
    const [deskOn, setDeskOn] = useState(false)
    const [deskEnv, setDeskEnv] = useState(0)
    const [deskViewIdx, setDeskViewIdx] = useState(0)
    const [deskCal, setDeskCal] = useState<DeskView | null>(null) // ?deskcal working copy
    const deskCalOn = useMemo(() => typeof window !== "undefined" && new URLSearchParams(window.location.search).has("deskcal"), [])
    const deskPortrait = stage ? stage.w / Math.max(1, stage.h) < 0.8 : false
    const deskBase = DESK_ENVIRONMENTS[deskEnv].views[deskViewIdx] ?? DESK_ENVIRONMENTS[deskEnv].views[0]
    const deskView = useMemo(() => (deskOn ? deskCal ?? deskResolveView(deskBase, deskPortrait) : null), [deskOn, deskBase, deskPortrait, deskCal])
    const pickDeskEnv = useCallback((i: number) => { setDeskCal(null); setDeskEnv(i); setDeskViewIdx(0) }, [])
    const pickDeskView = useCallback((i: number) => { setDeskCal(null); setDeskViewIdx(i) }, [])
    // warm the browser cache with the other view of this environment (not the GPU)
    useEffect(() => {
        if (!deskOn || typeof window === "undefined") return
        const t = window.setTimeout(() => {
            for (const v of DESK_ENVIRONMENTS[deskEnv].views) {
                if (v === deskBase) continue
                const im = new Image(); im.crossOrigin = "anonymous"; im.src = deskImageSrc(deskResolveView(v, deskPortrait))
            }
        }, 600)
        return () => window.clearTimeout(t)
    }, [deskOn, deskEnv, deskBase, deskPortrait])
''')
op('''        <RoundButton key="parts" title="Parts" onClick={() => openPanel("parts")} pulse={!partsEverOpened} pulseColor={PULSE_BLUE} accentColor={accentColor}><PlusIcon /></RoundButton>,
        <RoundButton key="colour" title="Colours" onClick={() => openPanel("colour")} accentColor={accentColor}><EyedropperIcon /></RoundButton>,
        <RoundButton key="cart" title="Checkout" onClick={() => openPanel("checkout")} accentColor={accentColor}>''',
'''        <RoundButton key="desk" title={deskOn ? "Leave desk mode" : "Desk mode"} onClick={() => setDeskOn((v) => !v)} solid={deskOn} accentColor={accentColor}><CameraIcon /></RoundButton>,
        <RoundButton key="parts" title="Parts" onClick={() => openPanel("parts")} pulse={!partsEverOpened} pulseColor={PULSE_BLUE} accentColor={accentColor} style={deskOn ? DESK_BUTTON : undefined}><PlusIcon /></RoundButton>,
        <RoundButton key="colour" title="Colours" onClick={() => openPanel("colour")} accentColor={accentColor} style={deskOn ? DESK_BUTTON : undefined}><EyedropperIcon /></RoundButton>,
        <RoundButton key="cart" title="Checkout" onClick={() => openPanel("checkout")} accentColor={accentColor} style={deskOn ? DESK_BUTTON : undefined}>''')
op('''        <RoundButton key="light" title="Lighting" onClick={cycleLighting} accentColor={accentColor}><LightIcon /></RoundButton>,''',
   '''        <RoundButton key="light" title={deskOn ? "Lighting follows the photograph in desk mode" : "Lighting"} onClick={cycleLighting} disabled={deskOn} accentColor={accentColor} style={deskOn ? DESK_BUTTON : undefined}><LightIcon /></RoundButton>,''')
op('                        apiRef={viewerApiRef} onPreviews={setPreviews} resetViewKey={resetViewKey}\n                    />\n',
   '''                        apiRef={viewerApiRef} onPreviews={setPreviews} resetViewKey={resetViewKey}
                        desk={deskView}
                    />

                    {/* Desk mode: which photograph. Bottom-left, clear of the round buttons. */}
                    <AnimatePresence>
                        {deskOn && !placementMode && !adjusting && !(isNarrow && panelOpen) && (
                            <motion.div key="desk-select"
                                initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, transition: { duration: 0.15 } }} transition={UI_BOUNCE(0.2)}
                                style={{ position: "absolute", left: GAP, bottom: isNarrow ? GAP + CIRCLE + GAP : GAP }}>
                                <DeskSelector envs={DESK_ENVIRONMENTS} env={deskEnv} view={deskViewIdx} onEnv={pickDeskEnv} onView={pickDeskView} accentColor={accentColor} narrow={isNarrow} />
                            </motion.div>
                        )}
                    </AnimatePresence>
                    {deskCalOn && deskView && <DeskCalibrator view={deskView} onChange={setDeskCal} />}
''')
op('{(showStartHint || awaitingTap || (envLabel && !awaitingTap)) && (', '{((showStartHint && !deskOn) || awaitingTap || (envLabel && !awaitingTap)) && (')

import os
if os.environ.get('DEBUGHOOK'): A('            worldRef.current = world\n', '            ;(window as any).__w = world\n')
# standard colours: light grey side covers, dark grey structure, MYTY blue pucks and power button
op('    corners: "#f5f5f5", frame: "#3d3d3d", motherboard_bracket: "#2e7d32", "variousparts_1.003": "#2e7d32", variousparts_1003: "#2e7d32",\n    ssd_holder: "#2e7d32",',
   '    corners: "#3d3d3d", frame: "#3d3d3d", motherboard_bracket: "#3d3d3d", "variousparts_1.003": "#3d3d3d", variousparts_1003: "#3d3d3d",\n    ssd_holder: "#3d3d3d",')
op('powerbutton_holder: "#3d3d3d", powerbutton_button: "#ff0000",', 'powerbutton_holder: "#3d3d3d", powerbutton_button: "#0a0192",')
op('    if (isSidePanelPartName(name)) return "#2e7d32"\n', '    if (isSidePanelPartName(name)) return "#c9c9c9"\n')
op('const DEFAULT_PUCK_COLOR = "#ff0000" // red standard swatch', 'const DEFAULT_PUCK_COLOR = "#0a0192" // MYTY blue')

op('const SCREENHOLDER_PLASTIC_COLOR = "#3d3d3d"', 'const SCREENHOLDER_PLASTIC_COLOR = "#c9c9c9"')

s = base
for find, repl, count in ops:
    n = s.count(find)
    if n != count: print('ANCHOR MISMATCH', n, repr(find[:70])); sys.exit(1)
    s = s.replace(find, repl)
open('ProductConfigurator.tsx', 'w').write(s)
if not os.environ.get('DEBUGHOOK'): json.dump([[f, r] for f, r, _ in ops], open('desk/ops.json', 'w'))
print('ok', len(base), '->', len(s), 'ops', len(ops))
