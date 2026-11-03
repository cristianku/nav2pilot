#!/usr/bin/env python3
"""Builds docs/apps-privacy/index.html, the privacy policy of all the apps on one page.

Each app keeps its own page in docs/<app>/privacy/index.html (Nav2Pilot: docs/privacy/index.html); this script
copies their language sections under one heading per app, so every app can use the same address
(stores accept .../apps-privacy/ for all of them; .../apps-privacy/#<app> jumps to one app).
The general page is published on cristianku.com/apps-privacy/ (WordPress) and on GitHub Pages.
It also writes docs/site/<app>/privacy/index.html, the app pages for cristianku.com/<app>/privacy/: the same
text, with the note about the website instead of the one about GitHub Pages.
Risono's policy lives on its own site (risono.14bodhi.com/privacy/, repo risono-site): here it only gets a section
that points there, and its two pages forward to it.

    python3 scripts/build-apps-privacy.py
"""
import re
from pathlib import Path

DOCS = Path(__file__).resolve().parent.parent / "docs"
UPDATED = "3 November 2026"
SITE_POLICY = "https://cristianku.com/privacy-policy/"
MAIL = '<a href="mailto:admin@14bodhi.com">admin@14bodhi.com</a>'

# Alphabetical: (anchor, name, source page)
APPS = [
    ("cubomi", "Cubomi", DOCS / "cubomi/privacy/index.html"),
    ("groovejam", "GrooveJam", DOCS / "groovejam/privacy/index.html"),
    ("nav2pilot", "Nav2Pilot", DOCS / "privacy/index.html"),
    ("risono", "Risono", "https://risono.14bodhi.com/privacy/"),
]

# Apps whose policy lives on their own website (the source is its address): the general page keeps a short
# section that points there, and docs/<app>/privacy/ and docs/site/<app>/privacy/ forward to it.
ELSEWHERE = {
    "en": ("The privacy policy of {name} is on the app’s website:", ""),
    "it": ("L’informativa sulla privacy di {name} è sul sito dell’app:", "it/"),
    "de": ("Die Datenschutzerklärung von {name} steht auf der Website der App:", "#policy-de"),
    "pl": ("Polityka prywatności aplikacji {name} znajduje się na jej stronie internetowej:", "#policy-pl"),
}

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

# The web page section of the app pages on cristianku.com.
SITE_NOTE = {
    "en": "This policy covers the {name} app, not your visit to this website, which has its own privacy policy: ",
    "it": "Questa informativa riguarda l’app {name}, non la visita a questo sito, che ha la sua informativa: ",
    "de": "Diese Datenschutzerklärung gilt für die App {name}, nicht für den Besuch dieser Website, für die ihre "
          "eigene Datenschutzerklärung gilt: ",
    "pl": "Niniejsza polityka dotyczy aplikacji {name}, a nie wizyty na tej stronie internetowej, która ma własną "
          "politykę prywatności: ",
}


def elsewhere_url(url, code):
    suffix = ELSEWHERE[code][1]
    return url + suffix if suffix.startswith("#") else url.replace(".com/", ".com/" + suffix, 1)


def elsewhere_section(anchor, name, url):
    parts = [f'<section id="{anchor}">', f"<h2>{name}</h2>"]
    for code, (text, _) in ELSEWHERE.items():
        link = elsewhere_url(url, code)
        parts.append(f'<div id="{anchor}-{code}" lang="{code}">\n<p>{text.format(name=name)} '
                     f'<a href="{link}">{link}</a></p>\n</div>')
    parts.append("</section>")
    return "\n".join(parts)


def forward_page(anchor, name, url):
    """docs/<app>/privacy/ (GitHub Pages) and the cristianku.com copy: they only send the visitor on."""
    page = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{name} — Privacy Policy</title>
<link rel="canonical" href="{url}">
<meta http-equiv="refresh" content="0; url={url}">
</head>
<body>
<p>The privacy policy of {name} has moved to <a href="{url}">{url}</a></p>
</body>
</html>
"""
    for out in (DOCS / anchor / "privacy/index.html", DOCS / "site" / anchor / "privacy/index.html"):
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(page)
        print(f"wrote {out}")


def app_section(anchor, name, source):
    if isinstance(source, str):
        return elsewhere_section(anchor, name, source)
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


def site_page(anchor, name, source):
    """The app page for cristianku.com: the web page section points to the website's policy."""
    html = source.read_text()

    def replace_section(match):
        code = match.group(1)
        heading = match.group(2)
        site = SITE_NOTE[code].format(name=name) + f'<a href="{SITE_POLICY}">{SITE_POLICY}</a>'
        return re.sub(rf"<h3>{re.escape(heading)}</h3>.*?(?=<h3>|\Z)",
                      f"<h3>{heading}</h3>\n<p>{site}</p>\n\n", match.group(0), flags=re.S)

    html = re.sub(rf'<section id="([a-z]+)" lang="[a-z]+">.*?<h3>({WEB_PAGE_HEADINGS})</h3>.*?</section>',
                  replace_section, html, flags=re.S)
    if "GitHub Pages" in html:
        raise SystemExit(f"GitHub Pages still named in the site page of {source}")
    out = DOCS / "site" / anchor / "privacy/index.html"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(html)
    print(f"wrote {out}")


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

    for anchor, name, source in APPS:
        if isinstance(source, str):
            forward_page(anchor, name, source)
        else:
            site_page(anchor, name, source)


if __name__ == "__main__":
    main()
