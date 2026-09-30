#!/usr/bin/env python3
"""Сборка энциклопедии: parts/*.html -> book.html -> PDF (Chromium), двухпроходное оглавление."""
import glob, os, re, subprocess, sys
from pypdf import PdfReader, PdfWriter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_PDF = os.path.join(HERE, "..", "Энциклопедия_эндокринология_циркадные_ритмы_фитотерапия_микотерапия.pdf")
CHROME = "/opt/pw-browsers/chromium"
PARTS = {"endo": "Часть I · Эндокринология", "circ": "Часть II · Циркадная биология и сон",
         "herb": "Часть III · Фитотерапия", "myco": "Часть IV · Лечебные грибы и споры",
         "life": "Часть V · Практика, безопасность, справочник"}


def read(p):
    with open(os.path.join(HERE, p), encoding="utf-8") as f:
        return f.read()


def chapters(body):
    return re.findall(r'<section class="chapter p-(\w+)" id="(c\d+)" data-title="([^"]+)"', body)


def toc_html(chs, pages):
    out, cur = [], None
    for part, cid, title in chs:
        if part != cur:
            cur = part
            out.append(f'<div class="part p-{part}"><span>{PARTS[part]}</span></div>')
        pg = pages.get(cid, "")
        out.append(f'<a class="it p-{part}" href="#{cid}"><span class="n">{cid[1:]}.</span>'
                   f'<span>{title}</span><span class="d"></span><span class="pg">{pg}</span></a>')
    return "\n".join(out)


def render(pages):
    body = "\n".join(read(p) for p in sorted(glob.glob(os.path.join(HERE, "parts", "*.html"))))
    chs = chapters(body)
    body = body.replace("<!--TOC-->", toc_html(chs, pages))
    html = ('<!doctype html><html lang="ru"><head><meta charset="utf-8">'
            '<title>Энциклопедия: эндокринология, циркадные ритмы, фитотерапия и лечебные грибы</title>'
            '<link rel="stylesheet" href="style.css"></head><body>' + body + "</body></html>")
    path = os.path.join(HERE, "book.html")
    with open(path, "w", encoding="utf-8") as f:
        f.write(html)
    tmp = os.path.join(HERE, "book.tmp.pdf")
    subprocess.run([CHROME, "--headless=new", "--no-sandbox", "--disable-gpu", "--no-pdf-header-footer",
                    "--virtual-time-budget=20000", "--run-all-compositor-stages-before-draw",
                    f"--print-to-pdf={tmp}", "file://" + path], check=True, capture_output=True)
    return tmp, chs


def page_map(pdf, chs):
    """Номера страниц глав по ссылкам оглавления (Chromium пишет внутренние ссылки как /Dest)."""
    r = PdfReader(pdf)
    idx = {p.indirect_reference.idnum: i + 1 for i, p in enumerate(r.pages)}
    dests = []
    for page in r.pages[:6]:
        for a in page.get("/Annots") or []:
            a = a.get_object()
            d = a.get("/Dest")
            if d is None and "/A" in a:
                d = a["/A"].get("/D")
            if d is None:
                continue
            if isinstance(d, str) or hasattr(d, "startswith"):
                d = r.named_destinations.get(str(d))
                d = d.page if d is not None else None
            else:
                d = d[0]
            if d is not None:
                dests.append(idx.get(d.idnum))
    ids = [c[1] for c in chs]
    return dict(zip(ids, dests)) if len(dests) >= len(ids) else {}


def main():
    tmp, chs = render({})
    pages = page_map(tmp, chs)
    print("chapters:", len(chs), "resolved:", len(pages))
    tmp, _ = render(pages)
    pages2 = page_map(tmp, chs)
    if pages2 != pages:
        tmp, _ = render(pages2)
    w = PdfWriter(clone_from=tmp)
    w.add_metadata({"/Title": "Энциклопедия: эндокринология, циркадные ритмы, фитотерапия и лечебные грибы",
                    "/Author": "Claude (Anthropic) по запросу пользователя", "/Subject": "Справочное издание, 2026"})
    for part, cid, title in chs:
        if cid in pages2 and pages2[cid]:
            w.add_outline_item(f"{cid[1:]}. {title}", pages2[cid] - 1)
    with open(OUT_PDF, "wb") as f:
        w.write(f)
    os.remove(tmp)
    print("pages:", len(PdfReader(OUT_PDF).pages), "->", os.path.abspath(OUT_PDF))


if __name__ == "__main__":
    sys.exit(main())
