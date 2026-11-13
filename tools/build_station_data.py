#!/usr/bin/env python3
"""Build core/src/main/resources/hyderabad_metro.json from OpenStreetMap.

Usage:
    python3 tools/build_station_data.py            # fetch from Overpass, then build
    python3 tools/build_station_data.py osm.json   # build from a saved Overpass response

Station order comes from the OSM route relations (one per direction). Both
directions are checked against each other, and each station's coordinate is
the average of its platform nodes. Display names are normalised via NAME_FIXES.

Data © OpenStreetMap contributors, ODbL 1.0 (https://www.openstreetmap.org/copyright).
"""
import json
import math
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "core/src/main/resources/hyderabad_metro.json"

OVERPASS_HOSTS = [
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass-api.de/api/interpreter",
]
QUERY = """[out:json][timeout:60];
(relation["type"="route"]["route"~"subway|light_rail|monorail"]["network"="Hyderabad Metro"](17.25,78.25,17.60,78.65););
out body;node(r);out body;"""

# One direction per line; this sets the station order used in the app.
LINES = [
    {"id": "red", "name": "Red Line", "color": "#E53935", "relation": 8297636, "reverse": 8297689},
    {"id": "blue", "name": "Blue Line", "color": "#1E88E5", "relation": 8297688, "reverse": 5468841},
    {"id": "green", "name": "Green Line", "color": "#43A047", "relation": 10692235, "reverse": 10692234},
]

# OSM name (after stripping line suffixes) -> display name used in the app.
NAME_FIXES = {
    "Mahatma Gandhi Bus Station": "MG Bus Station",
    "JBS": "JBS Parade Ground",
    "Parade Grounds": "Parade Ground",
    "L. B. Nagar": "LB Nagar",
    "S. R. Nagar": "SR Nagar",
    "Dilsukh Nagar": "Dilsukhnagar",
    "Lakdi-ka-pul": "Lakdikapul",
    "Road No 5 Jubilee Hills": "Road No. 5 Jubilee Hills",
}

TRANSFER_SECONDS = 300  # walk + wait when changing lines; refine from real rides


def fetch():
    body = urllib.parse.urlencode({"data": QUERY}).encode()
    for host in OVERPASS_HOSTS:
        try:
            req = urllib.request.Request(host, body, {"User-Agent": "metro-station-tracker-dev/0.1"})
            with urllib.request.urlopen(req, timeout=90) as resp:
                return json.load(resp)
        except Exception as e:  # try the next mirror
            print(f"{host} failed: {e}", file=sys.stderr)
    sys.exit("All Overpass hosts failed")


def clean_name(raw):
    name = re.sub(r"\s*\((Red|Blue|Green) Line\)$", "", raw)
    name = re.sub(r"\s+Metro Station$", "", name)
    return NAME_FIXES.get(name, name)


def slug(name):
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def stops(rel, nodes):
    result = []
    for m in rel["members"]:
        if m["type"] == "node" and m["role"].startswith("stop"):
            n = nodes[m["ref"]]
            tags = n.get("tags", {})
            result.append((clean_name(tags.get("name:en") or tags["name"]), n["lat"], n["lon"]))
    return result


def build(osm):
    nodes = {e["id"]: e for e in osm["elements"] if e["type"] == "node"}
    rels = {e["id"]: e for e in osm["elements"] if e["type"] == "relation"}

    coords = {}  # station id -> list of (lat, lon) platform positions
    names = {}
    lines = []
    for line in LINES:
        fwd = stops(rels[line["relation"]], nodes)
        back = stops(rels[line["reverse"]], nodes)
        fwd_names = [s[0] for s in fwd]
        if fwd_names != [s[0] for s in reversed(back)]:
            sys.exit(f"{line['id']}: the two directions disagree on station order")
        for name, lat, lon in fwd + back:
            sid = slug(name)
            names[sid] = name
            coords.setdefault(sid, []).append((lat, lon))
        ids = [slug(n) for n in fwd_names]
        lines.append({"id": line["id"], "name": line["name"], "color": line["color"], "stationIds": ids})

    stations = [
        {
            "id": sid,
            "name": names[sid],
            "lat": round(sum(p[0] for p in pts) / len(pts), 6),
            "lng": round(sum(p[1] for p in pts) / len(pts), 6),
        }
        for sid, pts in coords.items()
    ]
    on_lines = {}
    for line in lines:
        for sid in line["stationIds"]:
            on_lines.setdefault(sid, []).append(line["id"])
    transfers = [{"stationId": sid, "seconds": TRANSFER_SECONDS} for sid, ls in on_lines.items() if len(ls) > 1]

    return {
        "network": "Hyderabad Metro Rail",
        "schemaVersion": 1,
        "attribution": "© OpenStreetMap contributors (ODbL)",
        "lines": lines,
        "stations": sorted(stations, key=lambda s: s["id"]),
        "transfers": transfers,
    }


def haversine_m(a, b):
    lat1, lon1, lat2, lon2 = map(math.radians, (a["lat"], a["lng"], b["lat"], b["lng"]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6_371_000 * math.asin(math.sqrt(h))


def report(data):
    by_id = {s["id"]: s for s in data["stations"]}
    for line in data["lines"]:
        ids = line["stationIds"]
        gaps = [round(haversine_m(by_id[a], by_id[b])) for a, b in zip(ids, ids[1:])]
        print(f"{line['name']}: {len(ids)} stations, {by_id[ids[0]]['name']} -> {by_id[ids[-1]]['name']}, "
              f"gaps {min(gaps)}-{max(gaps)} m")
    print(f"{len(data['stations'])} unique stations; interchanges: "
          + ", ".join(by_id[t["stationId"]]["name"] for t in data["transfers"]))


if __name__ == "__main__":
    osm = json.load(open(sys.argv[1])) if len(sys.argv) > 1 else fetch()
    data = build(osm)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
    report(data)
    print(f"wrote {OUT.relative_to(ROOT)}")
