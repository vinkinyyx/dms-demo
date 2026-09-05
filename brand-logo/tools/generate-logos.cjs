/* MySolMed brand logo generator (v2)
 * One consistent logo: Quicksand rounded lowercase, navy #0B2545 + cyan dot #00B4D8.
 * Geometry: wordmark dot sampled from the original brochure logo; mark dot sampled
 * from the original app mark (m + raised cyan dot). Tight glyph ink boxes measured
 * via Canvas2D actualBoundingBox*. Outputs SVG (embedded woff2) + high-res PNG.
 * Run: node brand-logo/tools/generate-logos.cjs
 */
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const ROOT = path.resolve(__dirname, '..');
const OUT = { svg: path.join(ROOT, 'svg'), png: path.join(ROOT, 'png') };
for (const d of Object.values(OUT)) fs.mkdirSync(d, { recursive: true });

const NAVY = '#0B2545', CYAN = '#00B4D8', WHITE = '#FFFFFF';
const woffB64 = fs.readFileSync(path.join(ROOT, 'fonts', 'Quicksand-VF.woff2')).toString('base64');

// kind config: word = "mysolmed" lockup; mark = "m" + dot.
const CFG = {
  word: { text: 'mysolmed', weight: 600, em: 1000,
          // dot position relative to tight text ink box (sampled from original wordmark):
          // gap right of text end to dot center ≈0.109×textW; dot cy ≈0.638 down ink box; r≈0.178×inkH
          dotGapCx: 0.109, dotCyFrac: 0.62, dotRf: 0.178, pad: 0.16 },
  mark: { text: 'm', weight: 700, em: 1000,
          // dot relative to tight m ink box (sampled from original app mark):
          // cx = mRight + 0.263×mW ; cy = mTop - 0.575×mH ; r = 0.212×mH
          dotGapCx: 0.263, dotTop: 0.575, dotRfH: 0.212, pad: 0.10 },
};

const fontFace = `
@font-face{font-family:'Q6';font-weight:600;font-display:swap;src:url(data:font/woff2;base64,${woffB64}) format('woff2');}
@font-face{font-family:'Q7';font-weight:700;font-display:swap;src:url(data:font/woff2;base64,${woffB64}) format('woff2');}`;

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setContent(`<!doctype html><html><head><style>${fontFace.replace(/@font-face/g,'@font-face')}</style></head>
  <body><canvas id="c" width="100" height="100"></canvas></body></html>`);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(200);

  // Measure tight ink box (baseline y=0): {left,right,top(-asc),bottom(+desc),w,h}
  async function measure(kind) {
    const g = CFG[kind];
    return await page.evaluate(({ text, weight, em }) => {
      const c = document.getElementById('c'), ctx = c.getContext('2d');
      ctx.font = `${weight} ${em}px ${weight >= 680 ? 'Q7' : 'Q6'}, sans-serif`;
      const m = ctx.measureText(text);
      const left = -m.actualBoundingBoxLeft, right = m.actualBoundingBoxRight;
      const top = -m.actualBoundingBoxAscent, bottom = m.actualBoundingBoxDescent;
      return { left, right, top, bottom, w: right - left, h: bottom - top };
    }, { text: g.text, weight: g.weight, em: g.em });
  }

  function layout(kind) {
    const g = CFG[kind], b = MEAS[kind];
    let dcx, dcy, dr;
    if (kind === 'word') {
      dcx = b.right + g.dotGapCx * b.w;
      dcy = b.top + g.dotCyFrac * b.h; // baseline-ish
      dr = g.dotRf * b.h;
    } else {
      dcx = b.right + g.dotGapCx * b.w;
      dcy = b.top - g.dotTop * b.h;
      dr = g.dotRfH * b.h;
    }
    const minX = Math.min(b.left, dcx - dr), maxX = Math.max(b.right, dcx + dr);
    const minY = Math.min(b.top, dcy - dr), maxY = Math.max(b.bottom, dcy + dr);
    const cw = maxX - minX, ch = maxY - minY;
    const px = cw * g.pad, py = ch * g.pad;
    return { b, dcx, dcy, dr, vx: minX - px, vy: minY - py, vw: cw + px * 2, vh: ch + py * 2 };
  }

  function buildSvg(kind, letterColor, dotColor) {
    const g = CFG[kind], L = layout(kind);
    const fam = g.weight >= 680 ? 'Q7' : 'Q6';
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${L.vx.toFixed(1)} ${L.vy.toFixed(1)} ${L.vw.toFixed(1)} ${L.vh.toFixed(1)}" role="img" aria-label="MySolMed">
<defs><style>${fontFace}</style></defs>
<text x="0" y="0" font-family="'${fam}',sans-serif" font-weight="${g.weight}" font-size="${g.em}" fill="${letterColor}">${g.text}</text>
<circle cx="${L.dcx.toFixed(1)}" cy="${L.dcy.toFixed(1)}" r="${L.dr.toFixed(1)}" fill="${dotColor}"/>
</svg>`;
  }

  const MEAS = { word: await measure('word'), mark: await measure('mark') };
  console.log('measure', JSON.stringify(MEAS));

  const variants = [
    ['mysolmed-wordmark',       'word', NAVY,  CYAN],
    ['mysolmed-wordmark-white', 'word', WHITE, CYAN],
    ['mysolmed-wordmark-mono',  'word', NAVY,  NAVY],
    ['mysolmed-mark',           'mark', NAVY,  CYAN],
    ['mysolmed-mark-white',     'mark', WHITE, CYAN],
    ['mysolmed-mark-mono',      'mark', NAVY,  NAVY],
  ];
  for (const [file, kind, lc, dc] of variants) {
    fs.writeFileSync(path.join(OUT.svg, file + '.svg'), buildSvg(kind, lc, dc));
    console.log('svg', file);
  }

  // App icon / favicon: navy tile, white m + cyan dot, composition centered.
  function buildIcon() {
    const g = CFG.mark, L = layout('mark');
    const { b, dcx, dcy, dr } = L;
    // union content bounds
    const minX = Math.min(b.left, dcx - dr), maxX = Math.max(b.right, dcx + dr);
    const minY = Math.min(b.top, dcy - dr), maxY = Math.max(b.bottom, dcy + dr);
    const ccx = (minX + maxX) / 2, ccy = (minY + maxY) / 2;
    const cw = maxX - minX, chh = maxY - minY;
    const fit = Math.max(cw, chh);
    const half = fit / 2 / 0.60; // content occupies 60% of tile
    const tx = ccx - half, ty = ccy - half, ts = half * 2;
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${tx.toFixed(1)} ${ty.toFixed(1)} ${ts.toFixed(1)} ${ts.toFixed(1)}" role="img" aria-label="MySolMed">
<defs><style>${fontFace}</style></defs>
<rect x="${tx.toFixed(1)}" y="${ty.toFixed(1)}" width="${ts.toFixed(1)}" height="${ts.toFixed(1)}" fill="${NAVY}"/>
<text x="0" y="0" font-family="'Q7',sans-serif" font-weight="700" font-size="${g.em}" fill="${WHITE}">m</text>
<circle cx="${dcx.toFixed(1)}" cy="${dcy.toFixed(1)}" r="${dr.toFixed(1)}" fill="${CYAN}"/>
</svg>`;
  }
  const iconSvg = buildIcon();
  fs.writeFileSync(path.join(OUT.svg, 'mysolmed-appicon.svg'), iconSvg);
  fs.writeFileSync(path.join(OUT.svg, 'favicon.svg'), iconSvg);
  console.log('svg appicon + favicon');

  // ---- Rasterize (inline SVG -> element screenshot; uses page-loaded fonts) ----
  const shotPage = await browser.newPage({ viewport: { width: 1200, height: 1000 }, deviceScaleFactor: 4 });
  await shotPage.setContent('<!doctype html><html><head><style>' + fontFace + 'html,body{margin:0;padding:0;background:transparent}</style></head><body><div id="stage" style="position:fixed;left:0;top:0;"></div></body></html>');
  await shotPage.evaluate(() => document.fonts.ready);
  await shotPage.waitForTimeout(150);

  async function renderPng(svgStr, width, height, file, transparent = true) {
    // strip embedded <defs><style> to avoid duplicate fonts; page fontFace already has Q6/Q7
    const clean = svgStr.split('<defs>')[0] + (svgStr.split('</defs>')[1] || '');
    await shotPage.evaluate(({ svg, width, height }) => {
      const stage = document.getElementById('stage');
      stage.innerHTML = svg;
      const el = stage.querySelector('svg');
      el.setAttribute('width', width);
      el.setAttribute('height', height);
      el.style.display = 'block';
    }, { svg: clean, width, height });
    await shotPage.waitForFunction(() => { const t = document.querySelector('#stage text'); return !t || document.fonts.check('600 100px Q6'); }, null, { timeout: 5000 }).catch(() => {});
    await shotPage.waitForTimeout(120);
    const el = await shotPage.$('#stage svg');
    await el.screenshot({ path: path.join(OUT.png, file), omitBackground: transparent });
    console.log('png', file, width + 'x' + height);
  }

  const aspect = svg => { const m = svg.match(/viewBox="[\d.-]+ [\d.-]+ ([\d.]+) ([\d.]+)"/); return parseFloat(m[1]) / parseFloat(m[2]); };

  const markN = fs.readFileSync(path.join(OUT.svg, 'mysolmed-mark.svg'), 'utf8');
  const markW = fs.readFileSync(path.join(OUT.svg, 'mysolmed-mark-white.svg'), 'utf8');
  for (const s of [128, 256, 512]) {
    await renderPng(markN, s, s, `mysolmed-mark-${s}.png`);
    await renderPng(markW, s, s, `mysolmed-mark-white-${s}.png`);
  }
  const wdN = fs.readFileSync(path.join(OUT.svg, 'mysolmed-wordmark.svg'), 'utf8');
  const wdW = fs.readFileSync(path.join(OUT.svg, 'mysolmed-wordmark-white.svg'), 'utf8');
  for (const hh of [128, 256]) {
    const w = Math.round(hh * aspect(wdN));
    await renderPng(wdN, w, hh, `mysolmed-wordmark-${hh}.png`);
    await renderPng(wdW, w, hh, `mysolmed-wordmark-white-${hh}.png`);
  }
  for (const s of [16, 32, 48, 180, 192, 512]) {
    await renderPng(iconSvg, s, s, s <= 48 ? `favicon-${s}.png` : s === 180 ? 'apple-touch-icon.png' : `mysolmed-appicon-${s}.png`, false);
  }
  await browser.close();
  console.log('DONE');
})().catch(e => { console.error(e); process.exit(1); });
