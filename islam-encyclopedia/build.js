// Сборка энциклопедии: src/chapters/*.html -> build/book.html -> Islam-Encyclopedia.pdf
// Запуск: NODE_PATH=$(npm root -g) node build.js
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const ROOT = __dirname;
const chDir = path.join(ROOT, 'src', 'chapters');
const files = fs.readdirSync(chDir).filter(f => f.endsWith('.html')).sort();

let n = 0;
const toc = [];
const bodies = files.map(f => {
  let html = fs.readFileSync(path.join(chDir, f), 'utf8');
  // присвоение id заголовкам для оглавления
  html = html.replace(/<h1([^>]*)data-toc="(part|ch)"([^>]*)>([\s\S]*?)<\/h1>|<h2[^>]*>([\s\S]*?)<\/h2>/g, (m, a, kind, b, inner, h2) => {
    if (h2 !== undefined) {
      const last = toc[toc.length - 1];
      if (last && last.kind === 'ch') last.subs.push(h2.replace(/<[^>]+>/g, '').trim());
      return m;
    }
    const id = `toc-${++n}`;
    const num = (a + b).match(/data-num="([^"]*)"/);
    toc.push({ kind, id, num: num ? num[1] : '', title: inner.replace(/<[^>]+>/g, '').trim(), subs: [] });
    return `<h1 id="${id}"${a}data-toc="${kind}"${b}>${inner}</h1>`;
  });
  return html;
});

const tocHtml = ['<section class="toc chapter"><h1>Содержание</h1>'];
for (const t of toc) {
  if (t.kind === 'part') tocHtml.push(`<div class="tp">${t.num ? 'Часть ' + t.num + ' · ' : ''}${t.title}</div>`);
  else {
    tocHtml.push(`<div class="tc"><span class="nn">${t.num}</span><a href="#${t.id}">${t.title}</a><span class="dots"></span><span class="pg"><a href="#${t.id}"></a></span></div>`);
    if (t.subs.length) tocHtml.push(`<div class="ts">${t.subs.join(' · ')}</div>`);
  }
}
tocHtml.push('</section>');

const book = `<!doctype html><html lang="ru"><head><meta charset="utf-8">
<title>Ислам — Большая энциклопедия</title>
<style>${fs.readFileSync(path.join(ROOT,'src','fonts.css'),'utf8')}
${fs.readFileSync(path.join(ROOT,'src','style.css'),'utf8')}</style>
<script>window.PagedConfig={auto:true,after:()=>{window.__pagedDone=true}};</script>
<script src="paged.polyfill.js"></script></head><body>
${bodies.join('\n').replace('<!--TOC-->', tocHtml.join('\n'))}
</body></html>`;
fs.writeFileSync(path.join(ROOT, 'build', 'book.html'), book);

(async () => {
  const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
  const page = await browser.newPage();
  page.on('console', m => { if (m.type() === 'error') console.log('console:', m.text()); });
  await page.goto('file://' + path.join(ROOT, 'build', 'book.html'));
  await page.waitForFunction(() => window.__pagedDone === true, null, { timeout: 600000 });
  const pages = await page.evaluate(() => document.querySelectorAll('.pagedjs_page').length);
  const overflow = await page.evaluate(() => [...document.querySelectorAll('.pagedjs_page')].filter(p => {
    const c = p.querySelector('.pagedjs_page_content'); return c && c.scrollHeight > c.clientHeight + 4; }).map(p => p.dataset.pageNumber));
  console.log('pages:', pages, 'overflow:', overflow.join(',') || 'none');
  await page.pdf({ path: path.join(ROOT, 'Islam-Encyclopedia.pdf'), preferCSSPageSize: true, printBackground: true, outline: true, tagged: true });
  await browser.close();
})();
