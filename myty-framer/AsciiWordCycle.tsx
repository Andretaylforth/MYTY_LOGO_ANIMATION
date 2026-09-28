// MYTY stacked headline: "portable / powerful / modular."
// Request: each line cycles through three words (editable in Framer), on its own random
// 3–5 s timer. On change, the old word scrambles out and the new word is typed in with an
// ASCII effect: every newly typed letter flickers through ASCII glyphs before it resolves.
// Size, spacing and padding stay the same as the static text; everything is editable.
import { useEffect, useRef, useState, useId, startTransition, type CSSProperties } from "react"
import { addPropertyControls, ControlType, useIsStaticRenderer } from "framer"
import { useInView } from "framer-motion"

interface AsciiWordCycleProps {
    line1: string[]
    line2: string[]
    line3: string[]
    lastSuffix: string
    font: CSSProperties
    color: string
    size: number
    phoneSize: number
    phoneBreakpoint: number
    lineHeight: number
    minDelay: number
    maxDelay: number
    typeSpeed: number
    scrambleFrames: number
    glyphs: string
    align: "left" | "center" | "right"
    style?: CSSProperties
}

const DEFAULT_GLYPHS = "!<>-_\\/[]{}=+*^?#@$%&|~:;01"

function randomGlyph(set: string) {
    return set.charAt(Math.floor(Math.random() * set.length)) || "#"
}

// One animated line: shows words[index]; when index changes, scrambles out then types the new word.
function AsciiLine(props: {
    words: string[]
    active: boolean
    minDelay: number
    maxDelay: number
    typeSpeed: number
    scrambleFrames: number
    glyphs: string
    suffix: string
    className: string
    style: CSSProperties
}) {
    const { words, active, minDelay, maxDelay, typeSpeed, scrambleFrames, glyphs, suffix, className, style } = props
    const list = words.length ? words : [""]
    const [index, setIndex] = useState(0)
    const [text, setText] = useState(list[0])
    const [animating, setAnimating] = useState(false)
    const timers = useRef<number[]>([])

    const clear = () => {
        timers.current.forEach((t) => window.clearTimeout(t))
        timers.current = []
    }

    // Random 3–5 s wait, then change to the next word. Each line has its own timer.
    useEffect(() => {
        if (!active || list.length < 2 || animating) return
        const wait = minDelay + Math.random() * Math.max(0, maxDelay - minDelay)
        const id = window.setTimeout(() => startTransition(() => setIndex((i) => (i + 1) % list.length)), wait)
        return () => window.clearTimeout(id)
    }, [active, index, animating, list.length, minDelay, maxDelay])

    // Scramble out the old word, then type the new one letter by letter with ASCII flicker.
    useEffect(() => {
        const next = list[index % list.length]
        if (next === text && !animating) return
        clear()
        startTransition(() => setAnimating(true))
        const old = text
        let t = 0
        const step = Math.max(16, typeSpeed)
        // Phase 1: old word dissolves right-to-left into glyphs.
        for (let k = old.length; k >= 0; k--) {
            const keep = old.slice(0, k)
            const noise = Array.from({ length: Math.min(2, old.length - k) }, () => randomGlyph(glyphs)).join("")
            timers.current.push(window.setTimeout(() => startTransition(() => setText(keep + noise)), t))
            t += step * 0.5
        }
        // Phase 2: new word typed left-to-right; each new letter flickers `scrambleFrames` times.
        for (let k = 1; k <= next.length; k++) {
            for (let f = 0; f < scrambleFrames; f++) {
                const shown = next.slice(0, k - 1) + (next[k - 1] === " " ? " " : randomGlyph(glyphs))
                timers.current.push(window.setTimeout(() => startTransition(() => setText(shown)), t))
                t += step / Math.max(1, scrambleFrames)
            }
            const settled = next.slice(0, k)
            timers.current.push(window.setTimeout(() => startTransition(() => setText(settled)), t))
            t += step
        }
        timers.current.push(window.setTimeout(() => startTransition(() => setAnimating(false)), t))
        return clear
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [index])

    useEffect(() => clear, [])

    return (
        <span className={className} style={style} aria-label={list[index % list.length] + suffix}>
            <span aria-hidden="true">
                {text}
                {suffix}
            </span>
        </span>
    )
}

/**
 * @framerSupportedLayoutWidth any-prefer-fixed
 * @framerSupportedLayoutHeight auto
 * @framerIntrinsicWidth 600
 */
export default function AsciiWordCycle(props: AsciiWordCycleProps) {
    const {
        line1 = ["portable", "open source", "travel ready"],
        line2 = ["powerful", "desktop class", "gaming"],
        line3 = ["modular", "rebuildable", "repairable"],
        lastSuffix = ".",
        font,
        color = "#000000",
        size = 64,
        phoneSize = 40,
        phoneBreakpoint = 810,
        lineHeight = 1,
        minDelay = 3000,
        maxDelay = 5000,
        typeSpeed = 55,
        scrambleFrames = 3,
        glyphs = DEFAULT_GLYPHS,
        align = "left",
        style,
    } = props

    const isStatic = useIsStaticRenderer()
    const ref = useRef<HTMLDivElement>(null)
    const inView = useInView(ref)
    const [reduced, setReduced] = useState(false)
    const cls = "aw" + useId().replace(/[^a-zA-Z0-9]/g, "")

    useEffect(() => {
        if (typeof window === "undefined") return
        const mq = window.matchMedia("(prefers-reduced-motion: reduce)")
        const update = () => startTransition(() => setReduced(mq.matches))
        update()
        mq.addEventListener("change", update)
        return () => mq.removeEventListener("change", update)
    }, [])

    const active = !isStatic && inView && !reduced
    const { fontSize: _ignored, ...fontRest } = font ?? {}
    const lineStyle: CSSProperties = {
        display: "block",
        margin: 0,
        color,
        lineHeight,
        whiteSpace: "pre",
        textAlign: align,
        ...fontRest,
    }
    const shared = { active, minDelay, maxDelay, typeSpeed, scrambleFrames, glyphs, className: "aw-line", style: lineStyle }

    return (
        <div ref={ref} className={cls} style={{ position: "relative", textAlign: align, ...style }}>
            <style>{`
                .${cls} .aw-line{font-size:${size}px}
                @media (max-width:${phoneBreakpoint - 1}px){.${cls} .aw-line{font-size:${phoneSize}px}}
            `}</style>
            <AsciiLine words={line1} suffix="" {...shared} />
            <AsciiLine words={line2} suffix="" {...shared} />
            <AsciiLine words={line3} suffix={lastSuffix} {...shared} />
        </div>
    )
}

const words = (title: string, defaults: string[]) => ({
    type: ControlType.Array,
    title,
    control: { type: ControlType.String },
    defaultValue: defaults,
    maxCount: 6,
})

addPropertyControls(AsciiWordCycle, {
    line1: words("Line 1 Words", ["portable", "open source", "travel ready"]),
    line2: words("Line 2 Words", ["powerful", "desktop class", "gaming"]),
    line3: words("Line 3 Words", ["modular", "rebuildable", "repairable"]),
    lastSuffix: { type: ControlType.String, title: "Last Line End", defaultValue: "." },
    font: { type: ControlType.Font, title: "Font", controls: "extended", displayFontSize: false, defaultValue: { letterSpacing: "0px" } },
    color: { type: ControlType.Color, title: "Color", defaultValue: "#000000" },
    size: { type: ControlType.Number, title: "Size", defaultValue: 64, min: 8, max: 300, unit: "px" },
    phoneSize: { type: ControlType.Number, title: "Phone Size", defaultValue: 40, min: 8, max: 200, unit: "px" },
    phoneBreakpoint: { type: ControlType.Number, title: "Phone below", defaultValue: 810, min: 320, max: 1600, unit: "px" },
    lineHeight: { type: ControlType.Number, title: "Line Height", defaultValue: 1, min: 0.6, max: 2, step: 0.01 },
    minDelay: { type: ControlType.Number, title: "Min Wait (ms)", defaultValue: 3000, min: 500, max: 20000, step: 100 },
    maxDelay: { type: ControlType.Number, title: "Max Wait (ms)", defaultValue: 5000, min: 500, max: 20000, step: 100 },
    typeSpeed: { type: ControlType.Number, title: "Type (ms)", defaultValue: 55, min: 16, max: 400 },
    scrambleFrames: { type: ControlType.Number, title: "ASCII Flicker", defaultValue: 3, min: 0, max: 10, step: 1 },
    glyphs: { type: ControlType.String, title: "ASCII Glyphs", defaultValue: DEFAULT_GLYPHS },
    align: { type: ControlType.Enum, title: "Align", options: ["left", "center", "right"], defaultValue: "left", displaySegmentedControl: true },
})
