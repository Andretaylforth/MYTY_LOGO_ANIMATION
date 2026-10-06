// Usage: node render.cjs out.mp4            -> full render
//        node render.cjs --stills 1,5,9,... -> PNG stills in stills/
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const { spawn } = require('child_process');
const http = require('http'), fs = require('fs'), path = require('path');
const root = __dirname;
const server = http.createServer((q, r) => { const f = path.join(root, decodeURIComponent(q.url.split('?')[0])); fs.readFile(f, (e, b) => { if (e) { r.writeHead(404); r.end(); return; } r.writeHead(200, { 'Content-Type': f.endsWith('.html') ? 'text/html' : f.endsWith('.js') ? 'text/javascript' : 'font/woff2' }); r.end(b); }); }).listen(0);
(async () => {
  const port = server.address().port;
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  p.on('pageerror', e => console.error('PAGE ERROR', e));
  await p.goto(`http://localhost:${port}/boot.html?still`);
  await p.evaluate(() => BOOT.ready);
  const grab = async (t) => Buffer.from((await p.evaluate(t => { BOOT.renderAt(t); return document.getElementById('c').toDataURL('image/png'); }, t)).split(',')[1], 'base64');
  if (process.argv[2] === '--stills') {
    fs.mkdirSync(path.join(root, 'stills'), { recursive: true });
    for (const t of process.argv[3].split(',').map(Number)) fs.writeFileSync(path.join(root, 'stills', `t${t.toFixed(2)}.png`), await grab(t));
  } else {
    const { FPS, DURATION } = await p.evaluate(() => BOOT);
    const ff = spawn('ffmpeg', ['-v', 'error', '-y', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-', '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', process.argv[2]], { stdio: ['pipe', 'inherit', 'inherit'] });
    const n = Math.round(FPS * DURATION);
    for (let i = 0; i < n; i++) { const buf = await grab(i / FPS); if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r)); if (i % 150 === 0) console.log('frame', i, '/', n); }
    ff.stdin.end(); await new Promise(r => ff.on('close', r));
  }
  await b.close(); server.close();
})();
