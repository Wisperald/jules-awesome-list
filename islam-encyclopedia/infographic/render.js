// Рендер инфографики: timeline.html -> Islam-Timeline.pdf + Islam-Timeline.png
// Запуск: NODE_PATH=$(npm root -g) node render.js
const path = require('path');
const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
  const page = await browser.newPage();
  page.on('pageerror', e => console.log('pageerror:', e.message));
  await page.goto('file://' + path.join(__dirname, 'timeline.html'));
  await page.waitForFunction(() => window.__done === true, null, { timeout: 60000 });
  const { w, h } = await page.evaluate(() => window.__size);
  console.log('size:', w, 'x', h);
  await page.setViewportSize({ width: w, height: h });
  await page.pdf({ path: path.join(__dirname, 'Islam-Timeline.pdf'), width: w + 'px', height: (h + 2) + 'px', printBackground: true, pageRanges: '1' });
  const p2 = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1.5 });
  await p2.goto('file://' + path.join(__dirname, 'timeline.html'));
  await p2.waitForFunction(() => window.__done === true);
  await p2.screenshot({ path: path.join(__dirname, 'Islam-Timeline.png'), fullPage: true });
  await browser.close();
})();
