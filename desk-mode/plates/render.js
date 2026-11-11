const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const [,, env, view, spp = 96, w = 2400, h = 1600, tag = ""] = process.argv;
  const b = await chromium.launch({ args: ["--use-gl=swiftshader", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist"] });
  const p = await b.newPage({ viewport: { width: 800, height: 600 } });
  p.on("console", m => { if (m.type() === "error") console.log("ERR", m.text().slice(0, 300)) });
  p.on("pageerror", e => console.log("PAGEERR", String(e).slice(0, 400)));
  const t0 = Date.now();
  await p.goto(`http://localhost:8765/plategen.html?env=${env}&view=${view}&spp=${spp}&w=${w}&h=${h}`);
  await p.waitForFunction("window.__done === true", null, { timeout: 900000, polling: 1000 });
  const { full, small } = await p.evaluate("({ full: window.__full, small: window.__small })");
  const name = `env0${env}${view.toLowerCase()}${tag}`;
  fs.writeFileSync(`public/plates/${name}.jpg`, Buffer.from(full.split(",")[1], "base64"));
  fs.writeFileSync(`public/plates/${name}-s.jpg`, Buffer.from(small.split(",")[1], "base64"));
  console.log(name, ((Date.now() - t0) / 1000).toFixed(1) + "s", fs.statSync(`public/plates/${name}.jpg`).size, fs.statSync(`public/plates/${name}-s.jpg`).size);
  await b.close();
})();
