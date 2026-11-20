#!/usr/bin/env python3
"""Refresh the static IDs/positions used to join the Zurich and Singapore live feeds.

Run manually with Python 3 and pyproj. See the data sources and licences in README.md.
No occupancy or predicted availability belongs in the app bundle.
"""
import json
from pathlib import Path
from urllib.parse import parse_qs, urlparse
from urllib.request import urlopen

from pyproj import Transformer

OUT = Path(__file__).resolve().parents[1] / "app/src/main/resources/parking"


def fetch(url):
    with urlopen(url, timeout=30) as response:
        return json.load(response)


def write(name, rows):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / (name + ".json")).write_text(
        "{\n" + ",\n".join(json.dumps(k) + ":" + json.dumps(v, ensure_ascii=False)
                            for k, v in sorted(rows.items())) + "\n}\n", encoding="utf-8")


if __name__ == "__main__":
    transform = Transformer.from_crs("EPSG:3414", "EPSG:4326", always_xy=True)
    data = fetch("https://data.gov.sg/api/action/datastore_search?"
                 "resource_id=d_23f946fa557947f93a8043bbef41dd09&limit=5000")["result"]
    assert len(data["records"]) == data["total"]
    sg = {}
    for r in data["records"]:
        if r["short_term_parking"] == "NO":
            continue
        lon, lat = transform.transform(float(r["x_coord"]), float(r["y_coord"]))
        if 1.2 < lat < 1.48 and 103.6 < lon < 104.1:
            sg[r["car_park_no"]] = [round(lat, 6), round(lon, 6), r["address"]]
    zh = {}
    for f in fetch("https://www.ogd.stadt-zuerich.ch/wfs/geoportal/Oeffentlich_zugaengliche_Parkhaeuser?"
                   "service=WFS&version=1.0.0&request=GetFeature&typeName=poi_parkhaus_view&outputFormat=GeoJSON&srsName=EPSG:4326")["features"]:
        r = f["properties"]
        key = parse_qs(urlparse(r["link_pls"] or "").query).get("pid", [])
        if len(key) == 1 and f.get("geometry"):
            lon, lat = f["geometry"]["coordinates"]
            zh[key[0]] = [lat, lon, r["adresse"], int(r["anzahl_oeffentliche_pp"] or 0)]
    assert len(sg) > 1500 and len(zh) > 20
    write("singapore", sg)
    write("zurich", zh)
