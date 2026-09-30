// Рендер HTML -> PDF через Chromium (Playwright)
const path = require('path');
const { chromium } = require(process.env.PW_PATH || 'playwright');
(async () => {
  const proxy = process.env.HTTPS_PROXY ? { server: process.env.HTTPS_PROXY } : undefined;
  const browser = await chromium.launch({ proxy });
  const page = await browser.newPage();
  const src = path.resolve(__dirname, '../build/encyclopedia.html');
  await page.goto('file://' + src, { waitUntil: 'networkidle', timeout: 120000 });
  await page.evaluate(() => document.fonts.ready);
  await page.pdf({
    path: path.resolve(__dirname, '../Энциклопедия_китайской_акупунктуры.pdf'),
    format: 'A4', printBackground: true, preferCSSPageSize: true,
    displayHeaderFooter: true,
    headerTemplate: '<div></div>',
    footerTemplate: '<div style="font-size:7px;width:100%;padding:0 14mm;color:#64748b;display:flex;justify-content:space-between;font-family:sans-serif"><span>Энциклопедия современной китайской акупунктуры</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>',
  });
  await browser.close();
  console.log('pdf ok');
})();
