#!/usr/bin/env python3
"""Trace 'Logo Anim 30018-0090-1.mp4' frame by frame into a Lottie animation.

Each video frame's white shapes are vectorised with potrace (bezier curves)
and stored as one shape layer that is visible for exactly that frame, so the
Lottie plays back the original frame for frame at 24 fps.

Usage: python3 trace.py path/to/frames_dir   (frames as f_001.png ... from ffmpeg)
"""
import glob
import json
import os
import sys

import cv2
import numpy as np
import potrace

OUT = os.path.dirname(os.path.abspath(__file__))
FPS = 24
W, H = 1920, 1080
BG_BGR = np.array([140, 42, 0], float)   # #002A8C sampled from the video
UP = 4                                    # upscale before tracing for sub-pixel edges
# Region that contains every shape across the whole animation (plus margin).
RX0, RY0, RX1, RY1 = 880, 190, 1090, 645
# Tight canvas for the cropped export (keeps the same relative framing).
CROP = (890, 200, 1075, 635)


def trace_frame(img):
    roi = img[RY0:RY1, RX0:RX1].astype(float)
    diff = np.abs(roi - BG_BGR).sum(2)
    solid = diff > 300
    if not solid.any():
        return None, None
    # Sample the colour from the shape interior only (edges are blended with the blue).
    interior = cv2.erode(solid.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool)
    fg = roi[interior if interior.any() else solid].mean(0)
    axis = roi[solid].mean(0) - BG_BGR
    big = cv2.resize(roi, None, fx=UP, fy=UP, interpolation=cv2.INTER_CUBIC)
    alpha = ((big - BG_BGR) @ axis) / (axis @ axis)
    # Soften video compression noise on the edges so potrace fits clean curves.
    alpha = cv2.GaussianBlur(alpha, (0, 0), UP * 0.6)
    mask = alpha > 0.5
    # potracer traces the False pixels, so pass the inverted mask.
    curves = potrace.Bitmap(~mask).trace(turdsize=8 * UP, alphamax=1.1, opticurve=True,
                                        opttolerance=0.6)
    paths = [curve_to_lottie(c) for c in curves]
    color = [round(float(fg[2]) / 255, 4), round(float(fg[1]) / 255, 4),
             round(float(fg[0]) / 255, 4), 1]
    return paths, color


def pt(p):
    return [round(RX0 + p.x / UP, 1), round(RY0 + p.y / UP, 1)]


def curve_to_lottie(curve):
    v, i, o = [pt(curve.start_point)], [[0, 0]], [[0, 0]]
    for seg in curve.segments:
        if seg.is_corner:
            for q in (seg.c, seg.end_point):
                v.append(pt(q)); i.append([0, 0]); o.append([0, 0])
        else:
            cur = v[-1]
            c1, c2, end = pt(seg.c1), pt(seg.c2), pt(seg.end_point)
            o[-1] = [round(c1[0] - cur[0], 1), round(c1[1] - cur[1], 1)]
            v.append(end)
            i.append([round(c2[0] - end[0], 1), round(c2[1] - end[1], 1)])
            o.append([0, 0])
    # The last vertex closes back onto the first: fold it in.
    i[0] = i[-1]
    v, i, o = v[:-1], i[:-1], o[:-1]
    return {"c": True, "v": v, "i": i, "o": o}


def same_shapes(a, b, tol=0.35):
    if len(a) != len(b):
        return False
    for p, q in zip(a, b):
        if len(p["v"]) != len(q["v"]):
            return False
        for key in ("v", "i", "o"):
            if np.abs(np.array(p[key]) - np.array(q[key])).max() > tol:
                return False
    return True


def static(v):
    return {"a": 0, "k": v}


def transform(offset=(0, 0)):
    return {"a": static([0, 0, 0]), "p": static([offset[0], offset[1], 0]),
            "s": static([100, 100, 100]), "r": static(0), "o": static(100)}


def group_tr():
    return {"ty": "tr", "p": static([0, 0]), "a": static([0, 0]), "s": static([100, 100]),
            "r": static(0), "o": static(100), "sk": static(0), "sa": static(0)}


def build(frames, background, crop):
    w, h, off = (W, H, (0, 0))
    if crop:
        x0, y0, x1, y1 = CROP
        w, h, off = x1 - x0, y1 - y0, (-x0, -y0)
    layers = []
    for n, (paths, color) in enumerate(frames):
        if not paths:
            continue
        prev = layers[-1] if layers else None
        if prev and prev["op"] == n and same_shapes(prev["_paths"], paths):
            prev["op"] = n + 1          # frame is unchanged: keep showing the last one
            continue
        items = [{"ty": "sh", "ks": static(p)} for p in paths]
        # Even-odd fill so inner contours (the four holes) are cut out.
        items.append({"ty": "fl", "c": static(color), "o": static(100), "r": 2})
        items.append(group_tr())
        layers.append({"ddd": 0, "ind": len(layers) + 1, "ty": 4, "nm": f"frame {n + 1}",
                       "sr": 1, "ks": transform(off), "ao": 0, "ip": n, "op": n + 1,
                       "st": 0, "bm": 0, "shapes": [{"ty": "gr", "it": items}], "_paths": paths})
    for layer in layers:
        del layer["_paths"]
    if background:
        layers.append({"ddd": 0, "ind": len(layers) + 1, "ty": 4, "nm": "background",
                       "sr": 1, "ks": transform(), "ao": 0, "ip": 0, "op": len(frames),
                       "st": 0, "bm": 0, "shapes": [{"ty": "gr", "it": [
                           {"ty": "rc", "d": 1, "p": static([w / 2, h / 2]),
                            "s": static([w, h]), "r": static(0)},
                           {"ty": "fl", "c": static([0, 42 / 255, 140 / 255, 1]),
                            "o": static(100), "r": 1},
                           group_tr()]}]})
    return {"v": "5.7.4", "fr": FPS, "ip": 0, "op": len(frames), "w": w, "h": h,
            "nm": "MYTY logo animation (original)", "ddd": 0, "assets": [], "layers": layers}


if __name__ == "__main__":
    files = sorted(glob.glob(os.path.join(sys.argv[1], "*.png")))
    frames = [trace_frame(cv2.imread(f)) for f in files]
    outputs = {
        "myty-logo-original.json": (True, False),
        "myty-logo-original-transparent.json": (False, False),
        "myty-logo-original-cropped.json": (False, True),
    }
    for name, (bg, crop) in outputs.items():
        with open(os.path.join(OUT, name), "w") as f:
            json.dump(build(frames, bg, crop), f, separators=(",", ":"))
        print(name, os.path.getsize(os.path.join(OUT, name)), "bytes")
