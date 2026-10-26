#!/usr/bin/env python3
"""Builds docs/apps-privacy/index.html, the privacy policy of all the apps on one page.

Each app keeps its own page in docs/<app>/privacy/index.html (Nav2Pilot: docs/privacy/index.html); this script
copies their language sections under one heading per app, so every app can use the same address
(stores accept .../apps-privacy/ for all of them; .../apps-privacy/#<app> jumps to one app).
The general page is published on cristianku.com/apps-privacy/ (WordPress) and on GitHub Pages.

    python3 scripts/build-apps-privacy.py
"""
import re
from pathlib import Path

DOCS = Path(__file__).resolve().parent.parent / "docs"
UPDATED = "26 October 2026"
SITE_POLICY = "https://cristianku.com/privacy-policy/"
MAIL = '<a href="mailto:admin@14bodhi.com">admin@14bodhi.com</a>'

# Alphabetical: (anchor, name, source page)
APPS = [
    ("cubomi", "Cubomi", DOCS / "cubomi/privacy/index.html"),
    ("groovejam", "GrooveJam", DOCS / "groovejam/privacy/index.html"),
    ("nav2pilot", "Nav2Pilot", DOCS / "privacy/index.html"),
    ("risono", "Risono", DOCS / "risono/privacy/index.html"),
]

LANGUAGE_NAMES = {"en": "English", "it": "Italiano", "de": "Deutsch", "pl": "Polski"}

# The app pages say they are hosted on GitHub Pages; the general page has one note about the website instead.
WEB_PAGE_HEADINGS = "This web page|Questa pagina web|Diese Webseite|Ta strona internetowa"

INTRO = {
    "en": ("These are the privacy policies of the apps developed by Cristian Zantedeschi, Switzerland, and published "
           "under the developer name “14 Bodhi”. None of them contains advertising, analytics or tracking. Each app "
           "has its own section below, which says exactly which data it handles.",
           f"These policies cover the apps. For this website, see its own privacy policy: "
           f'<a href="{SITE_POLICY}">{SITE_POLICY}</a>',
           "Privacy contact:"),
    "it": ("Queste sono le informative sulla privacy delle app sviluppate da Cristian Zantedeschi, Svizzera, e "
           "pubblicate con il nome sviluppatore “14 Bodhi”. Nessuna contiene pubblicità, statistiche d’uso o "
           "tracciamento. Ogni app ha qui sotto la sua sezione, che dice esattamente quali dati tratta.",
           f"Queste informative riguardano le app. Per questo sito vale la sua informativa: "
           f'<a href="{SITE_POLICY}">{SITE_POLICY}</a>',
           "Contatto privacy:"),
    "de": ("Hier findest du die Datenschutzerklärungen der Apps, die Cristian Zantedeschi, Schweiz, entwickelt und "
           "unter dem Entwicklernamen „14 Bodhi“ veröffentlicht. Keine davon enthält Werbung, Analyse oder Tracking. "
           "Jede App hat unten ihren eigenen Abschnitt, der genau angibt, welche Daten sie verarbeitet.",
           f"Diese Erklärungen gelten für die Apps. Für diese Website gilt ihre eigene Datenschutzerklärung: "
           f'<a href="{SITE_POLICY}">{SITE_POLICY}</a>',
           "Kontakt für Datenschutz:"),
    "pl": ("Poniżej znajdują się polityki prywatności aplikacji tworzonych przez Cristiana Zantedeschiego ze "
           "Szwajcarii i publikowanych pod nazwą dewelopera „14 Bodhi”. Żadna z nich nie zawiera reklam, analityki "
           "ani śledzenia. Każda aplikacja ma poniżej własną sekcję, która dokładnie opisuje, jakie dane przetwarza.",
           f"Te polityki dotyczą aplikacji. Dla tej strony internetowej obowiązuje jej własna polityka prywatności: "
           f'<a href="{SITE_POLICY}">{SITE_POLICY}</a>',
           "Kontakt w sprawie prywatności:"),
}


def app_section(anchor, name, source):
    html = source.read_text()
    updated = re.search(r'<p class="meta">(.*?)</p>', html, re.S).group(1).strip()
    sections = re.findall(r'<section id="([a-z]+)" lang="[a-z]+">(.*?)</section>', html, re.S)
    if not sections:
        raise SystemExit(f"no language sections in {source}")
    links = " ".join(f'<a href="#{anchor}-{code}">{LANGUAGE_NAMES[code]}</a>' for code, _ in sections)
    parts = [f'<section id="{anchor}">', f"<h2>{name}</h2>", f'<p class="meta">{updated}</p>',
             f'<p class="lang">{links}</p>']
    for code, body in sections:
        body = re.sub(rf"<h3>({WEB_PAGE_HEADINGS})</h3>.*?(?=<h3>|\Z)", "", body, flags=re.S)
        body = body.replace("<h3>", "<h4>").replace("</h3>", "</h4>")
        body = body.replace("<h2>", "<h3>").replace("</h2>", "</h3>")
        parts.append(f'<div id="{anchor}-{code}" lang="{code}">{body.rstrip()}\n</div>')
    parts.append("</section>")
    return "\n".join(parts)


def main():
    style = re.search(r"<style>.*?</style>", APPS[0][2].read_text(), re.S).group(0)
    style = style.replace("</style>", "  h4 { font-size: 1rem; margin: 22px 0 4px; }\n</style>")
    intro = "\n".join(
        f'<div lang="{code}">\n<p>{text}</p>\n<p>{site}</p>\n<p>{contact} {MAIL}</p>\n</div>'
        for code, (text, site, contact) in INTRO.items()
    )
    index = "\n".join(f'  <li><a href="#{anchor}">{name}</a></li>' for anchor, name, _ in APPS)
    body = "\n\n".join(app_section(*app) for app in APPS)
    page = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Apps by 14 Bodhi — Privacy Policy</title>
<meta name="description" content="Privacy policies of the apps by 14 Bodhi: Cubomi, GrooveJam, Nav2Pilot, Risono.">
{style}
<body>
<main>

<h1>Apps by 14 Bodhi — Privacy Policy</h1>
<p class="meta">Last updated: {UPDATED}</p>

<ul>
{index}
</ul>

<div class="summary">
{intro}
</div>

{body}

</main>
</body>
</html>
"""
    out = DOCS / "apps-privacy/index.html"
    out.parent.mkdir(exist_ok=True)
    out.write_text(page)
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
