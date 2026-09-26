#!/usr/bin/env python3
"""Generate the jazzed-up MYTY logo animation in vector formats.

One timeline spec drives two outputs so they stay identical:
  - myty-logo.svg / myty-logo-transparent.svg : self-contained animated SVG (SMIL)
  - myty-logo.json / myty-logo-transparent.json : Lottie (Bodymovin) animation

Everything is authored in "logo units": the finished logo is a 100x100
rounded square centred on (0, 0). A parent transform places it on the canvas.
"""
import json
import math
import os

OUT = os.path.dirname(os.path.abspath(__file__))

W, H = 1920, 1080
FPS = 60
DUR = 4.0                     # seconds, loops seamlessly (starts and ends empty-ish)
SCALE = 2.8                   # px per logo unit -> logo is 280px on a 1080p canvas
BG = "#002A8C"                # sampled from the original render
GLOW = "#3D6BFF"
INK = "#F5F3F2"

# Easings (cubic-bezier control points; all within [0,1] so SMIL accepts them).
LIN = (0, 0, 1, 1)
OUT_ = (0.16, 1, 0.3, 1)      # fast start, soft landing
IN_ = (0.7, 0, 0.84, 0)       # accelerate (falling)
IO = (0.65, 0, 0.35, 1)
SNAP = (0.2, 0.9, 0.1, 1)


# A track is a list of (time_s, value, easing_to_next).
def T(*keys):
    return [(k[0], k[1], k[2] if len(k) > 2 else IO) for k in keys]


def const(v):
    return T((0, v))


# ---------------------------------------------------------------- timeline --
# Triangle: drops in, lands on the bar (seesaw), gets flung up, then dives into
# the finished logo.
tri = dict(
    pos=T((0.00, [0, -260], IN_), (0.38, [0, -34], OUT_), (0.52, [0, -46], IN_),
          (0.62, [0, -34], IO), (1.05, [0, -40], IN_), (1.22, [30, -16], OUT_),
          (1.55, [48, -120], IO), (2.40, [0, -130], IN_), (2.70, [0, -48], LIN),
          (2.78, [0, -40])),
    scale=T((0.00, [70, 150], IN_), (0.38, [150, 55], OUT_), (0.52, [90, 115], IO),
            (0.62, [100, 100], IO), (1.20, [100, 100], OUT_), (1.24, [140, 60], OUT_),
            (1.40, [85, 125], IO), (1.60, [100, 100], IO), (2.40, [100, 100], IN_),
            (2.70, [70, 150], LIN), (2.78, [0, 0])),
    rot=T((0, 0), (1.22, 0, OUT_), (1.55, 200, IO), (2.40, 360)),
    opacity=T((0, 100), (2.76, 100, LIN), (2.78, 0)),
)

# Bar A whips in from the right, becomes the logo body.
body = dict(
    pos=T((0.00, [620, 0], OUT_), (0.30, [620, 0], OUT_), (0.62, [-6, 0], IO),
          (0.74, [4, 0], IO), (0.84, [0, 0])),
    scale=T((0, [100, 100]), (0.30, [180, 70], OUT_), (0.62, [80, 125], IO),
            (0.78, [100, 100], IO), (2.70, [100, 100], OUT_), (2.80, [110, 88], IO),
            (2.98, [96, 104], IO), (3.15, [100, 100])),
    rot=T((0, 0), (1.20, 0, OUT_), (1.30, 9, IO), (1.44, -5, IO), (1.56, 2, IO),
          (1.66, 0)),
    size=T((0, [56, 16]), (0.86, [56, 16], SNAP), (1.02, [104, 14], IO),
           (1.10, [100, 16]), (1.66, [100, 16], IO), (1.80, [110, 8], SNAP),
           (2.02, [96, 108], IO), (2.14, [100, 98], IO), (2.24, [100, 100])),
    radius=T((0, 2), (1.80, 2, SNAP), (2.02, 25)),
    opacity=const(100),
)

# Bar B whips in from the left and merges into A.
bar_b = dict(
    pos=T((0.00, [-640, 0], OUT_), (0.52, [-640, 0], OUT_), (0.86, [-22, 0], LIN),
          (0.90, [-22, 0])),
    scale=T((0, [100, 100]), (0.52, [180, 70], OUT_), (0.86, [100, 100])),
    rot=const(0),
    size=const([56, 16]),
    radius=const(2),
    opacity=T((0, 100), (0.88, 100, LIN), (0.92, 0)),
)

# Holes punch out one by one with an overshoot.
HOLE_C = 21.75
HOLE_R = 18.75
holes = []
for i, (sx, sy) in enumerate([(-1, -1), (1, -1), (1, 1), (-1, 1)]):
    t0 = 2.02 + i * 0.07
    holes.append(dict(
        center=[sx * HOLE_C, sy * HOLE_C],
        d=T((0, 0), (t0, 0, OUT_), (t0 + 0.16, HOLE_R * 2 * 1.08, IO),
            (t0 + 0.30, HOLE_R * 2 * 0.96, IO), (t0 + 0.40, HOLE_R * 2)),
    ))

# Impact FX when the body snaps into the square.
IMPACT = 2.02
ring = dict(
    d=T((0, 0), (IMPACT, 110, OUT_), (IMPACT + 0.7, 330)),
    stroke=T((0, 0), (IMPACT - 0.001, 0, LIN), (IMPACT, 5, OUT_), (IMPACT + 0.7, 0)),
    opacity=T((0, 0), (IMPACT - 0.001, 0, LIN), (IMPACT, 100, LIN), (IMPACT + 0.7, 0)),
)
ring2 = dict(
    d=T((0, 0), (IMPACT + 0.12, 110, OUT_), (IMPACT + 0.95, 420)),
    stroke=T((0, 0), (IMPACT + 0.119, 0, LIN), (IMPACT + 0.12, 3, OUT_), (IMPACT + 0.95, 0)),
    opacity=T((0, 0), (IMPACT + 0.119, 0, LIN), (IMPACT + 0.12, 70, LIN), (IMPACT + 0.95, 0)),
)
sparks = []
for i in range(12):
    ang = i * 30 + (8 if i % 2 else 0)
    far = 150 if i % 2 == 0 else 115
    t0 = IMPACT + (0.0 if i % 2 == 0 else 0.05)
    sparks.append(dict(
        angle=ang,
        x=T((0, 58), (t0, 58, OUT_), (t0 + 0.55, far)),
        len=T((0, 0), (t0, 0, OUT_), (t0 + 0.12, 26, IO), (t0 + 0.55, 0)),
        opacity=T((0, 0), (t0 - 0.001, 0, LIN), (t0, 100, LIN), (t0 + 0.55, 0)),
    ))
glow = dict(
    d=const(520),
    opacity=T((0, 0), (IMPACT - 0.05, 0, OUT_), (IMPACT + 0.08, 55, IO),
              (IMPACT + 1.2, 22, IO), (DUR - 0.3, 22, IO), (DUR, 0)),
)
# Whole-logo fade at the very end so the loop restarts cleanly.
logo_fade = T((0, 100), (DUR - 0.25, 100, IN_), (DUR, 0))


# ------------------------------------------------------------------ helpers --
def pad(track):
    """Ensure keys at t=0 and t=DUR."""
    k = list(track)
    if k[0][0] > 0:
        k.insert(0, (0, k[0][1], LIN))
    if k[-1][0] < DUR:
        k.append((DUR, k[-1][1], LIN))
    return k


def fmt(v):
    if isinstance(v, list):
        return " ".join(fmt(x) for x in v)
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def sample(track, t):
    """Evaluate a track at time t (used for static initial attribute values)."""
    k = pad(track)
    for (t0, v0, e), (t1, v1, _) in zip(k, k[1:]):
        if t0 <= t <= t1:
            u = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            y = bez(e, u)
            if isinstance(v0, list):
                return [a + (b - a) * y for a, b in zip(v0, v1)]
            return v0 + (v1 - v0) * y
    return k[-1][1]


def bez(e, x):
    x1, y1, x2, y2 = e
    lo, hi = 0.0, 1.0
    for _ in range(40):
        m = (lo + hi) / 2
        bx = 3 * (1 - m) ** 2 * m * x1 + 3 * (1 - m) * m * m * x2 + m ** 3
        lo, hi = (m, hi) if bx < x else (lo, m)
    m = (lo + hi) / 2
    return 3 * (1 - m) ** 2 * m * y1 + 3 * (1 - m) * m * m * y2 + m ** 3


# --------------------------------------------------------------------- SVG --
def smil(attr, track, tf=None, map_=lambda v: v, tag="animate"):
    k = pad(track)
    vals = ";".join(fmt(map_(v)) for _, v, _ in k)
    times = ";".join(fmt(t / DUR) for t, _, _ in k)
    splines = ";".join(" ".join(fmt(c) for c in e) for _, _, e in k[:-1])
    extra = f' type="{tf}" additive="sum"' if tf else ""
    return (f'<{tag} attributeName="{attr}"{extra} dur="{DUR}s" repeatCount="indefinite" '
            f'calcMode="spline" values="{vals}" keyTimes="{times}" keySplines="{splines}"/>')


def svg_transform(el, inner):
    t = ('<g>' + smil("transform", el["pos"], "translate", tag="animateTransform")
         + '<g>' + smil("transform", el["rot"], "rotate", tag="animateTransform")
         + '<g>' + smil("transform", el["scale"], "scale", map_=lambda v: [v[0] / 100, v[1] / 100],
                         tag="animateTransform")
         + inner + '</g></g></g>')
    return t


def svg_rect(el, extra=""):
    s0 = sample(el["size"], 0)
    r0 = sample(el["radius"], 0)
    return (f'<rect x="{fmt(-s0[0]/2)}" y="{fmt(-s0[1]/2)}" width="{fmt(s0[0])}" height="{fmt(s0[1])}" '
            f'rx="{fmt(r0)}" fill="{INK}"{extra}>'
            + smil("width", el["size"], map_=lambda v: v[0])
            + smil("height", el["size"], map_=lambda v: v[1])
            + smil("x", el["size"], map_=lambda v: -v[0] / 2)
            + smil("y", el["size"], map_=lambda v: -v[1] / 2)
            + smil("rx", el["radius"])
            + smil("opacity", el["opacity"], map_=lambda v: v / 100)
            + '</rect>')


def build_svg(transparent):
    p = []
    p.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">')
    p.append('<title>MYTY logo animation</title>')
    p.append('<defs>')
    p.append(f'<radialGradient id="glow"><stop offset="0" stop-color="{GLOW}"/>'
             f'<stop offset="1" stop-color="{GLOW}" stop-opacity="0"/></radialGradient>')
    # Mask that punches the four holes out of the body.
    p.append('<mask id="holes" maskUnits="userSpaceOnUse" x="-400" y="-400" width="800" height="800">'
             '<rect x="-400" y="-400" width="800" height="800" fill="#fff"/>')
    for h in holes:
        cx, cy = h["center"]
        p.append(f'<circle cx="{fmt(cx)}" cy="{fmt(cy)}" r="0" fill="#000">'
                 + smil("r", h["d"], map_=lambda v: v / 2) + '</circle>')
    p.append('</mask></defs>')
    if not transparent:
        p.append(f'<rect width="{W}" height="{H}" fill="{BG}"/>')
    p.append(f'<g transform="translate({W/2} {H/2}) scale({SCALE})">')
    p.append(smil("opacity", logo_fade, map_=lambda v: v / 100))
    # background glow
    p.append(f'<circle r="{glow["d"][0][1]/2}" fill="url(#glow)" opacity="0">'
             + smil("opacity", glow["opacity"], map_=lambda v: v / 100) + '</circle>')
    for rg in (ring2, ring):
        p.append(f'<circle r="0" fill="none" stroke="{INK}" stroke-width="0" opacity="0">'
                 + smil("r", rg["d"], map_=lambda v: v / 2)
                 + smil("stroke-width", rg["stroke"])
                 + smil("opacity", rg["opacity"], map_=lambda v: v / 100) + '</circle>')
    for s in sparks:
        p.append(f'<g transform="rotate({s["angle"]})"><g>'
                 + smil("transform", _spark_pos(s), "translate", tag="animateTransform")
                 + f'<rect y="-1.6" width="0" height="3.2" rx="1.6" fill="{INK}" opacity="0">'
                 + smil("width", s["len"])
                 + smil("opacity", s["opacity"], map_=lambda v: v / 100) + '</rect></g></g>')
    p.append(svg_transform(bar_b, svg_rect(bar_b)))
    p.append(svg_transform(body, '<g mask="url(#holes)">' + svg_rect(body) + '</g>'))
    tri_path = f'<path d="M-7 -5 L7 -5 L0 6 Z" fill="{INK}" stroke="{INK}" stroke-width="1.5" stroke-linejoin="round">' \
               + smil("opacity", tri["opacity"], map_=lambda v: v / 100) + '</path>'
    p.append(svg_transform(tri, tri_path))
    p.append('</g></svg>')
    return "\n".join(p)


# ------------------------------------------------------------------ Lottie --
def rgb(hexs):
    h = hexs.lstrip("#")
    return [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)] + [1]


def lprop(track, map_=lambda v: v):
    k = pad(track)
    if len(k) == 2 and k[0][1] == k[1][1]:
        v = map_(k[0][1])
        return {"a": 0, "k": v}
    kfs = []
    for (t, v, e), nxt in zip(k, k[1:] + [None]):
        v = map_(v)
        kf = {"t": round(t * FPS, 3), "s": v if isinstance(v, list) else [v]}
        if nxt is not None:
            kf["o"] = {"x": e[0], "y": e[1]}
            kf["i"] = {"x": e[2], "y": e[3]}
        kfs.append(kf)
    return {"a": 1, "k": kfs}


def ltransform(pos=None, rot=None, scale=None, opacity=None):
    return {
        "a": {"a": 0, "k": [0, 0, 0]},
        "p": lprop(pos, lambda v: v + [0]) if pos else {"a": 0, "k": [0, 0, 0]},
        "r": lprop(rot) if rot else {"a": 0, "k": 0},
        "s": lprop(scale, lambda v: v + [100]) if scale else {"a": 0, "k": [100, 100, 100]},
        "o": lprop(opacity) if opacity else {"a": 0, "k": 100},
    }


def gtr(pos=None, rot=0, opacity=None):
    return {"ty": "tr", "p": {"a": 0, "k": pos or [0, 0]}, "a": {"a": 0, "k": [0, 0]},
            "s": {"a": 0, "k": [100, 100]}, "r": {"a": 0, "k": rot},
            "o": lprop(opacity) if opacity else {"a": 0, "k": 100},
            "sk": {"a": 0, "k": 0}, "sa": {"a": 0, "k": 0}}


def fill(color, rule=1, opacity=100):
    return {"ty": "fl", "c": {"a": 0, "k": rgb(color)}, "o": {"a": 0, "k": opacity}, "r": rule}


LAST = int(DUR * FPS)
_ind = [1]


def layer(name, shapes, ks, parent=1, ty=4):
    _ind[0] += 1
    L = {"ddd": 0, "ind": _ind[0], "ty": ty, "nm": name, "sr": 1, "ks": ks,
         "ao": 0, "ip": 0, "op": LAST, "st": 0, "bm": 0}
    if parent:
        L["parent"] = parent
    if ty == 4:
        L["shapes"] = shapes
    return L


def rect_shape(el):
    return {"ty": "rc", "d": 1, "p": {"a": 0, "k": [0, 0]}, "s": lprop(el["size"]),
            "r": lprop(el["radius"])}


def build_lottie(transparent):
    _ind[0] = 1
    layers = []
    # Parent null that places logo units on the canvas.
    root = {"ddd": 0, "ind": 1, "ty": 3, "nm": "logo_root", "sr": 1,
            "ks": ltransform(pos=const([W / 2, H / 2]), scale=const([SCALE * 100, SCALE * 100]),
                             opacity=logo_fade),
            "ao": 0, "ip": 0, "op": LAST, "st": 0, "bm": 0}

    layers.append(layer("triangle", [{"ty": "gr", "it": [
        {"ty": "sh", "ks": {"a": 0, "k": {"c": True, "v": [[-7, -5], [7, -5], [0, 6]],
                                           "i": [[0, 0]] * 3, "o": [[0, 0]] * 3}}},
        {"ty": "st", "c": {"a": 0, "k": rgb(INK)}, "o": {"a": 0, "k": 100}, "w": {"a": 0, "k": 1.5},
         "lc": 2, "lj": 2},
        fill(INK), gtr()]}],
        ltransform(tri["pos"], tri["rot"], tri["scale"], tri["opacity"])))

    # Body with holes: all paths share one even-odd fill so the circles cut out.
    body_items = [{"ty": "gr", "it": [rect_shape(body), gtr()]}]
    for h in holes:
        body_items.append({"ty": "gr", "it": [
            {"ty": "el", "d": 1, "p": {"a": 0, "k": h["center"]}, "s": lprop(h["d"], lambda v: [v, v])},
            gtr()]})
    body_items += [fill(INK, rule=2), gtr()]
    layers.append(layer("body", [{"ty": "gr", "it": body_items}],
                        ltransform(body["pos"], body["rot"], body["scale"], body["opacity"])))

    layers.append(layer("bar_b", [{"ty": "gr", "it": [rect_shape(bar_b), fill(INK), gtr()]}],
                        ltransform(bar_b["pos"], None, bar_b["scale"], bar_b["opacity"])))

    for i, s in enumerate(sparks):
        spark_tr = gtr()
        spark_tr["p"] = lprop(_spark_pos(s))
        layers.append(layer(f"spark_{i}", [{"ty": "gr", "it": [
            {"ty": "rc", "d": 1, "p": lprop(s["len"], lambda v: [v / 2, 0]),
             "s": lprop(s["len"], lambda v: [v, 3.2]), "r": {"a": 0, "k": 1.6}},
            fill(INK), spark_tr]}],
            ltransform(None, const(s["angle"]), None, s["opacity"])))

    for n, rg in (("ring", ring), ("ring2", ring2)):
        layers.append(layer(n, [{"ty": "gr", "it": [
            {"ty": "el", "d": 1, "p": {"a": 0, "k": [0, 0]}, "s": lprop(rg["d"], lambda v: [v, v])},
            {"ty": "st", "c": {"a": 0, "k": rgb(INK)}, "o": {"a": 0, "k": 100},
             "w": lprop(rg["stroke"]), "lc": 2, "lj": 2},
            gtr()]}],
            ltransform(None, None, None, rg["opacity"])))

    layers.append(layer("glow", [{"ty": "gr", "it": [
        {"ty": "el", "d": 1, "p": {"a": 0, "k": [0, 0]}, "s": {"a": 0, "k": [520, 520]}},
        {"ty": "gf", "o": {"a": 0, "k": 100}, "r": 1, "t": 2,
         "s": {"a": 0, "k": [0, 0]}, "e": {"a": 0, "k": [260, 0]},
         "g": {"p": 2, "k": {"a": 0, "k": [0] + rgb(GLOW)[:3] + [1] + rgb(GLOW)[:3] + [0, 1, 1, 0]}}},
        gtr()]}],
        ltransform(None, None, None, glow["opacity"])))

    layers.append(root)
    if not transparent:
        layers.append(layer("background", [{"ty": "gr", "it": [
            {"ty": "rc", "d": 1, "p": {"a": 0, "k": [W / 2, H / 2]}, "s": {"a": 0, "k": [W, H]},
             "r": {"a": 0, "k": 0}},
            fill(BG), gtr()]}], ltransform(), parent=None))

    return {"v": "5.7.4", "fr": FPS, "ip": 0, "op": LAST, "w": W, "h": H,
            "nm": "MYTY logo animation", "ddd": 0, "assets": [], "layers": layers}


def _spark_pos(s):
    return [(t, [v, 0], e) for t, v, e in s["x"]]


if __name__ == "__main__":
    for transparent, suffix in ((False, ""), (True, "-transparent")):
        with open(os.path.join(OUT, f"myty-logo{suffix}.svg"), "w") as f:
            f.write(build_svg(transparent))
        with open(os.path.join(OUT, f"myty-logo{suffix}.json"), "w") as f:
            json.dump(build_lottie(transparent), f, separators=(",", ":"))
    print("wrote SVG + Lottie to", OUT)
