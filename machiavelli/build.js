// Сборка энциклопедии: src/parts/*.html -> src/encyclopedia.html -> Machiavelli_Encyclopedia.pdf
// Запуск: NODE_PATH=$(npm root -g) node build.js
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const SRC = path.join(__dirname, 'src');
const parts = fs.readdirSync(path.join(SRC, 'parts')).filter(f => f.endsWith('.html')).sort();
const body = parts.map(f => fs.readFileSync(path.join(SRC, 'parts', f), 'utf8')).join('\n');

const html = `<!doctype html>
<html lang="ru"><head><meta charset="utf-8">
<title>Никколо Макиавелли — Энциклопедия</title>
<link rel="stylesheet" href="style.css">
</head><body>
${body}
</body></html>`;
const htmlPath = path.join(SRC, 'encyclopedia.html');
fs.writeFileSync(htmlPath, html);

(async () => {
  const browser = await chromium.launch({
    executablePath: fs.existsSync('/opt/pw-browsers/chromium-1194/chrome-linux/chrome')
      ? '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' : undefined,
  });
  const page = await browser.newPage();
  await page.goto('file://' + htmlPath, { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  await page.pdf({
    path: path.join(__dirname, 'Machiavelli_Encyclopedia.pdf'),
    preferCSSPageSize: true,
    printBackground: true,
    outline: true,
    tagged: true,
  });
  await browser.close();
  console.log('OK:', parts.length, 'parts');
})();
