const W = 2000, H = 1342, cx = W / 2, cy = H / 2
const line = (a, b) => { const A = b[1] - a[1], B = a[0] - b[0]; return [A, B, -(A * a[0] + B * a[1])] }
const isect = (l, m) => { const d = l[0] * m[1] - m[0] * l[1]; return [(l[1] * m[2] - m[1] * l[2]) / d, (m[0] * l[2] - l[0] * m[2]) / d] }
const P = { dFL: [566.5, 714.3], dBL: [733, 657.8], dFR: [1684.3, 878.3], dBR: [1801.7, 697.8], sFL: [745.2, 629.6], sBL: [854.8, 592.6], sFR: [1606.1, 731.3] }
const vx = isect(line(P.dFL, P.dFR), line(P.sFL, P.sFR)), vz = isect(line(P.dFL, P.dBL), line(P.dFR, P.dBR)), vz2 = isect(line(P.sFL, P.sBL), line(P.dFR, P.dBR))
console.log("VPx", vx.map(v => v.toFixed(0)), "VPz", vz.map(v => v.toFixed(0)), "VPz(shelfL)", vz2.map(v => v.toFixed(0)))
const f2 = -((vx[0] - cx) * (vz[0] - cx) + (vx[1] - cy) * (vz[1] - cy)); const f = Math.sqrt(f2)
console.log("f", f.toFixed(0), "fov", (2 * Math.atan(H / 2 / f) * 180 / Math.PI).toFixed(2))
const norm = a => { const l = Math.hypot(...a); return a.map(v => v / l) }, cross = (a, b) => [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]], dot = (a, b) => a[0]*b[0]+a[1]*b[1]+a[2]*b[2]
// camera coords: x right, y up, z backward (three.js); pixel ray = (px-cx, -(py-cy), -f)
const ray = p => norm([p[0] - cx, -(p[1] - cy), -f])
let X = ray(vx).map(v => -v), Z = ray(vz)            // desk +x (to the right) and the direction of desk "depth" edges going away
Z = Z.map(v => -v)                      // desk +z points towards the viewer side (front of desk)
let Y = norm(cross(Z, X)); Z = norm(cross(X, Y))   // orthonormalise, keep X
console.log("X", X.map(v => v.toFixed(3)), "Y", Y.map(v => v.toFixed(3)), "Z", Z.map(v => v.toFixed(3)), "X.Zraw", dot(ray(vx), ray(vz)).toFixed(4))
// desk axes expressed in camera coords -> camera axes in desk coords: transpose
const toDesk = v => [dot(v, X), dot(v, Y), dot(v, Z)]
const fwd = toDesk([0, 0, -1]), up = toDesk([0, 1, 0]), right = toDesk([1, 0, 0])
const roll = Math.atan2(dot(right, [0, 1, 0]), dot(up, [0, 1, 0])) * 180 / Math.PI
console.log("forward(desk)", fwd.map(v => v.toFixed(4)), "roll deg", roll.toFixed(2), "pitch", (Math.asin(-fwd[1]) * 180 / Math.PI).toFixed(2), "az", (Math.atan2(-fwd[0], -fwd[2]) * 180 / Math.PI).toFixed(2))
// place: PC base centre pixel + apparent height -> camera distance
const base = JSON.parse(process.env.BASE || "[1085,655]"), hpx = +(process.env.HPX || 280), hM = 0.4156
const rb = toDesk(ray(base))
const proj = (p, cam) => { const d = [p[0] - cam[0], p[1] - cam[1], p[2] - cam[2]]; const xc = dot(d, right), yc = dot(d, up), zc = dot(d, fwd); return [cx + f * xc / zc, cy - f * yc / zc] }
let lo = 0.5, hi = 30
for (let i = 0; i < 60; i++) { const t = (lo + hi) / 2, cam = rb.map(v => -v * t); const top = proj([0, hM, 0], cam), bot = proj([0, 0, 0], cam); if (Math.hypot(top[0] - bot[0], top[1] - bot[1]) > hpx) lo = t; else hi = t }
const t = (lo + hi) / 2, cam = rb.map(v => -v * t)
console.log("dist", t.toFixed(3), "camera.position", cam.map(v => +v.toFixed(4)), "target", cam.map((v, i) => +(v + fwd[i] * 3).toFixed(4)), "roll", (-roll).toFixed(2))
const un = (p, hy) => { const r = toDesk(ray(p)); const k = (hy - cam[1]) / r[1]; return cam.map((v, i) => +(v + r[i] * k).toFixed(3)) }
for (const [n, p, h] of [["shelf FL", P.sFL, 0], ["shelf BL", P.sBL, 0], ["shelf FR", P.sFR, 0], ["shelf FL bottom", [745.2, 643.5], null], ["desk FL", P.dFL, "d"], ["desk BL", P.dBL, "d"], ["desk FR", P.dFR, "d"], ["desk BR", P.dBR, "d"], ["kb FL", [958, 738], "d"], ["calc FL", [1296, 668], 0]]) {
  if (h === null) continue; const hy = h === "d" ? -(+(process.env.RISER || 0.06)) : h; console.log(n, un(p, hy)) }
// riser: vertical px per metre at shelf front-left
const sfl = un(P.sFL, 0); const a = proj(sfl, cam), b2 = proj([sfl[0], -0.1, sfl[2]], cam); console.log("px per 10cm vertical at shelf FL", (b2[1] - a[1]).toFixed(1), "check sFL reproj", a.map(v => v.toFixed(1)))
console.log("pc corners px", [[-0.1723,0],[0.1723,0],[-0.1723,hM],[0.1723,hM]].map(([x, y]) => proj([x, y, 0.0443], cam).map(v => v.toFixed(0)).join(",")).join(" | "))
{ const fl = un([1835, 1215], -0.79); let lo = -0.8, hi = 2; for (let i = 0; i < 50; i++) { const y = (lo + hi) / 2; if (proj([fl[0], y, fl[2]], cam)[1] > 320) lo = y; else hi = y }
  console.log("lamp base", fl, "shade centre y", ((lo + hi) / 2).toFixed(3), "shade px x", proj([fl[0], (lo + hi) / 2, fl[2]], cam)[0].toFixed(0)) }
