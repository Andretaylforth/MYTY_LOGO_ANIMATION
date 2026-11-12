const W = 2000, H = +(process.env.H || 1333)
const x0 = -0.25323, x1 = 0.23456, y0 = -0.2557, y1 = 0.33269, z = 0.06269
const m8 = (x, y) => [738 + x / 0.7, 160 + y / 0.7]
const pts = process.env.SCENE === "5" ? [ [[x0,y1,z],m8(158.6,138.6)], [[x1,y1,z],m8(338.6,145.7)], [[x1,y0,z],m8(334.9,354.3)], [[x0,y0,z],m8(160,337.7)], [[x1,y1,-z],m8(354.9,142.9)], [[x1,y0,-z],m8(350.9,338.6)], [[x0,y1,-z],m8(162.9,133.7)] ]
  : [ [[x0,y1,z],[842.5,393.8]], [[x1,y1,z],[1209.4,362.5]], [[x1,y0,z],[1207.5,853]], [[x0,y0,z],[845,903]], [[x0,y1,-z],[765.6,387.5]], [[x0,y0,-z],[775,890.6]], [[x1,y1,-z],[1159,350]] ]
function proj(p, P) { // P: az, el, dist, tx, ty, tz, fov
  const [az, el, dist, tx, ty, tz, fov] = P
  const cam = [tx + dist * Math.cos(el) * Math.sin(az), ty + dist * Math.sin(el), tz + dist * Math.cos(el) * Math.cos(az)]
  const f = norm([tx - cam[0], ty - cam[1], tz - cam[2]]), r = norm(cross(f, [0, 1, 0])), u = cross(r, f)
  const d = [p[0] - cam[0], p[1] - cam[1], p[2] - cam[2]]
  const X = dot(d, r), Y = dot(d, u), Z = dot(d, f), k = (H / 2) / Math.tan(fov * Math.PI / 360)
  return [W / 2 + k * X / Z, H / 2 - k * Y / Z, cam]
}
const dot = (a, b) => a[0]*b[0]+a[1]*b[1]+a[2]*b[2], cross = (a, b) => [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]], norm = a => { const l = Math.hypot(...a); return a.map(v => v / l) }
const cost = P => { if (P[2] < 0.5 || P[6] < 5 || P[6] > 70) return 1e9; let s = 0; for (const [p, q] of pts) { const r = proj(p, P); s += (r[0]-q[0])**2 + (r[1]-q[1])**2 } return s }
function nm(f, x, step, it = 6000) { const n = x.length; let S = [x, ...x.map((_, i) => x.map((v, j) => j === i ? v + step[i] : v))].map(p => [p, f(p)])
  for (let k = 0; k < it; k++) { S.sort((a, b) => a[1] - b[1]); const c = S[0][0].map((_, j) => S.slice(0, n).reduce((s, p) => s + p[0][j], 0) / n), w = S[n][0]
    const at = t => c.map((v, j) => v + t * (w[j] - v)); let r = at(-1), fr = f(r)
    if (fr < S[0][1]) { const e = at(-2), fe = f(e); S[n] = fe < fr ? [e, fe] : [r, fr] } else if (fr < S[n-1][1]) S[n] = [r, fr]
    else { const o = at(0.5), fo = f(o); if (fo < S[n][1]) S[n] = [o, fo]; else S = S.map((p, i) => i === 0 ? p : (q => [q, f(q)])(p[0].map((v, j) => S[0][0][j] + 0.5 * (v - S[0][0][j])))) } }
  S.sort((a, b) => a[1] - b[1]); return S[0] }
let best = null
for (const fov of [12, 18, 25, 35]) for (const az of [-0.6, -0.3, -0.1, 0.1, 0.3, 0.6]) for (const el of [0.15, 0.35]) { let r = nm(cost, [az, el, 0.415*1.4159/ (2*Math.tan(fov*Math.PI/360)*0.37), 0, 0.05, 0, fov], [0.1, 0.1, 0.3, 0.05, 0.05, 0.05, 3]); r = nm(cost, r[0], [0.02, 0.02, 0.05, 0.01, 0.01, 0.01, 1]); if (!best || r[1] < best[1]) best = r }
const P = best[0]; console.log("rms px", Math.sqrt(best[1] / pts.length).toFixed(2), "params", P.map(v => +v.toFixed(4)))
for (const [p, q] of pts) { const r = proj(p, P); console.log(q, "->", r[0].toFixed(1), r[1].toFixed(1)) }
const cam = proj(pts[0][0], P)[2], RS = 1.4158730411780498, bx = (x0 + x1) / 2
const desk = v => [(v[0] - bx) / RS, (v[1] - y0) / RS, v[2] / RS].map(n => +n.toFixed(4))
console.log("camera.position", desk(cam), "target", desk([P[3], P[4], P[5]]), "fov", +P[6].toFixed(2))

// unproject image points onto a horizontal plane (desk-space height hy) to measure the scene
const camM = cam, RSc = RS
function unproj(px, py, hy) { const [az, el, dist, tx, ty, tz, fov] = P
  const f = norm([tx - camM[0], ty - camM[1], tz - camM[2]]), r = norm(cross(f, [0, 1, 0])), u = cross(r, f), k = (H / 2) / Math.tan(fov * Math.PI / 360)
  const d = [0,1,2].map(i => f[i] + r[i] * (px - W / 2) / k - u[i] * (py - H / 2) / k)
  const Y = y0 + hy * RSc, t = (Y - camM[1]) / d[1]; return desk([camM[0] + d[0] * t, Y, camM[2] + d[2] * t]) }
for (const [n, x, y, h] of JSON.parse(process.env.UN || "[]")) console.log(n, unproj(x, y, h))
