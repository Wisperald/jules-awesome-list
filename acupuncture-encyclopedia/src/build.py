# -*- coding: utf-8 -*-
"""Сборка HTML-энциклопедии; PDF рендерится render.js (Chromium)."""
import html
import os
from points import MERIDIANS, POINTS, EXTRAS, SEX_NOTES
import figures as F

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "build")
os.makedirs(OUT, exist_ok=True)

E = html.escape


def ccode(sex, who):
    """Условный код тела: M-/F- + буквы канала + двузначный номер (EX-HN3 -> EXHN03)."""
    if who.startswith("EX-"):
        grp = "".join(ch for ch in who[3:] if ch.isalpha())
        num = "".join(ch for ch in who[3:] if ch.isdigit())
        return f"{sex}-EX{grp}{int(num):02d}"
    mer = "".join(ch for ch in who if ch.isalpha())
    num = "".join(ch for ch in who if ch.isdigit())
    return f"{sex}-{mer}{int(num):02d}"


def marks(flags):
    m = []
    if "T" in flags:
        m.append('<span class="tg" title="триггер">TrP</span>')
    if "P" in flags:
        m.append('<span class="pg" title="беременность">Б</span>')
    if "D" in flags:
        m.append('<span class="dg" title="опасная зона">!</span>')
    return " ".join(m)


def name_cell(who, p):
    trig = "T" in p["flags"]
    cls = ' class="trig"' if trig else ""
    return (f'<b{cls}>{E(who)}</b><br><span{cls}>{E(p["py"])}</span> '
            f'<span class="cn">{E(p["cn"])}</span>')


def codes_cell(who):
    return (f'<span class="cm">{ccode("M", who)}</span><br>'
            f'<span class="cf">{ccode("F", who)}</span>')


def sex_note(who):
    if who in SEX_NOTES:
        m, f = SEX_NOTES[who]
        return f'<div class="sx"><span class="cm">♂</span> {E(m)}<br><span class="cf">♀</span> {E(f)}</div>'
    return ""


def point_rows(prefix, pts, extra=False):
    rows = []
    for p in pts:
        who = p["num"] if extra else f"{prefix}{p['num']}"
        rows.append(
            f'<tr><td class="c1">{name_cell(who, p)}</td><td class="c2">{codes_cell(who)}</td>'
            f'<td class="c3">{E(p["loc"])}{sex_note(who)}</td>'
            f'<td class="c4">{E(p["ind"])}</td>'
            f'<td class="c5">{E(p["cat"])}<div class="mk">{marks(p["flags"])}</div></td></tr>')
    return "".join(rows)


TABLE_HEAD = ('<thead><tr><th>Код ВОЗ / название</th><th>Условный код<br>♂ / ♀</th>'
              '<th>Локализация (WHO 2008)</th><th>Традиционные показания</th><th>Категория / метки</th></tr></thead>')


def meridian_section(i, m):
    code, ru, en, cn, aspect, hours, course = m
    pts = POINTS[code]
    col = F.COLORS[code]
    figs = F.meridian_figure(code)
    fig_html = "".join(
        f'<figure class="mfig"><div class="svgbox">{svg}</div><figcaption>{F.VIEW_NAMES[v]}</figcaption></figure>'
        for v, svg in figs)
    ntrig = sum("T" in p["flags"] for p in pts)
    npreg = sum("P" in p["flags"] for p in pts)
    cats = [f'{code}{p["num"]} — {E(p["cat"])}' for p in pts if p["cat"]]
    return f'''
<section class="meridian" id="m-{code}">
  <div class="mhead" style="--mc:{col}">
    <div class="mcode">{code}</div>
    <div><div class="mru">6.{i} {E(ru)}</div>
    <div class="men">{E(en)} · <span class="cn">{cn}</span> · {E(aspect)}</div></div>
    <div class="mstat"><b>{len(pts)}</b> точек<br><b>{ntrig}</b> TrP · <b>{npreg}</b> Б</div>
  </div>
  <div class="mbody">
    <div class="mtext">
      <h4>Ход наружной ветви</h4><p>{E(course)}</p>
      <h4>Время «максимума» по традиционным часам органов</h4><p>{hours} <span class="muted">(концепция цзы-у лю-чжу; научно не подтверждена — см. раздел 10.6)</span></p>
      <h4>Специальные точки канала</h4><ul class="cats">{"".join(f"<li>{c}</li>" for c in cats) or "<li>—</li>"}</ul>
    </div>
    <div class="mfigs">{fig_html}</div>
  </div>
  <table class="pts">{TABLE_HEAD}<tbody>{point_rows(code, pts)}</tbody></table>
</section>'''


def trig_list():
    items = []
    for m in MERIDIANS:
        for p in POINTS[m[0]]:
            if "T" in p["flags"]:
                items.append(f'<span class="chip"><b class="trig">{m[0]}{p["num"]}</b> {E(p["py"])}</span>')
    for p in EXTRAS:
        if "T" in p["flags"]:
            items.append(f'<span class="chip"><b class="trig">{p["num"]}</b> {E(p["py"])}</span>')
    return "".join(items), len(items)


def preg_list():
    out = []
    for m in MERIDIANS:
        for p in POINTS[m[0]]:
            if "P" in p["flags"]:
                out.append(f'{m[0]}{p["num"]}')
    return ", ".join(out)


def embedded_fonts():
    import base64
    import re
    fdir = os.path.join(HERE, "fonts")
    css = open(os.path.join(fdir, "fonts.css"), encoding="utf-8").read()

    def repl(m):
        data = open(os.path.join(fdir, m.group(1)), "rb").read()
        return "url(data:font/woff2;base64," + base64.b64encode(data).decode() + ")"
    return re.sub(r"url\(fonts/([^)]+)\)", repl, css) + "\n"


def build():
    css = embedded_fonts() + open(os.path.join(HERE, "style.css"), encoding="utf-8").read()
    band_svg, band_rows = F.hormone_band()
    tl, tn = trig_list()
    total = sum(len(POINTS[m[0]]) for m in MERIDIANS)
    toc_mer = "".join(f'<li><a href="#m-{m[0]}"><b>{m[0]}</b> — {E(m[1])}</a></li>' for m in MERIDIANS)
    mer_sections = "".join(meridian_section(i + 1, m) for i, m in enumerate(MERIDIANS))

    band_legend = "".join(
        f'<tr><td><b>{E(l)}</b></td><td>{s}</td><td>{E(t)}</td></tr>' for l, _, s, t in band_rows)

    body = open(os.path.join(HERE, "content.html"), encoding="utf-8").read()
    body = (body.replace("{{TOC_MER}}", toc_mer)
                .replace("{{MERIDIANS}}", mer_sections)
                .replace("{{OV_F}}", F.overview("F"))
                .replace("{{OV_B}}", F.overview("B"))
                .replace("{{OV_S}}", F.overview("S"))
                .replace("{{OV_H}}", F.overview("H"))
                .replace("{{LEGEND}}", "".join(
                    f'<span class="lg"><i style="background:{F.COLORS[m[0]]}"></i>{m[0]} · {E(m[1])}</span>' for m in MERIDIANS))
                .replace("{{EXTRAS}}", f'<table class="pts">{TABLE_HEAD}<tbody>{point_rows("", EXTRAS, extra=True)}</tbody></table>')
                .replace("{{TRIG_LIST}}", tl).replace("{{TRIG_N}}", str(tn))
                .replace("{{PREG_LIST}}", preg_list())
                .replace("{{TOTAL}}", str(total))
                .replace("{{CLOCK}}", F.organ_clock())
                .replace("{{BAND}}", band_svg).replace("{{BAND_LEGEND}}", band_legend)
                .replace("{{CYCLE}}", F.cycle_chart())
                .replace("{{TESTO}}", F.testo_chart())
                .replace("{{SEXNOTES}}", "".join(
                    f'<tr><td><b>{E(k)}</b></td><td class="mono">{ccode("M", k)}<br>{ccode("F", k)}</td><td>{E(v[0])}</td><td>{E(v[1])}</td></tr>'
                    for k, v in SEX_NOTES.items())))
    doc = f'''<!doctype html><html lang="ru"><head><meta charset="utf-8">
<title>Энциклопедия современной китайской акупунктуры</title>
<style>{css}</style></head><body>{body}</body></html>'''
    path = os.path.join(OUT, "encyclopedia.html")
    open(path, "w", encoding="utf-8").write(doc)
    print("written", path, "points", total)


if __name__ == "__main__":
    build()
