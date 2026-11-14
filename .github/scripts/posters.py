"""Fill in movie posters for data.json.

Runs on GitHub's servers (see .github/workflows/posters.yml). For every film in
movies.picks / movies.nf / movies.sh without a working "img", it finds a poster
(Wikipedia first, Arabic Wikipedia for Arabic titles, then Apple's US movie store),
saves a 400px JPEG in posters/, and points "img" at the GitHub Pages copy, so the
TV loads posters from the same site as the page instead of searching at runtime.
"""
import hashlib, io, json, os, re, sys, urllib.parse, urllib.request
from PIL import Image

PAGES = "https://hastv309.github.io/TV/"
UA = "HussamTVPosters/1.0 (https://github.com/hastv309/TV)"
AR = re.compile(r"[؀-ۿ]")


def get(url, raw=False):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        b = r.read()
    return b if raw else json.loads(b.decode("utf-8"))


def wiki(lang, query):
    q = urllib.parse.urlencode({
        "action": "query", "format": "json", "generator": "search", "gsrsearch": query,
        "gsrlimit": 3, "prop": "pageimages", "piprop": "original|thumbnail", "pithumbsize": 500, "pilicense": "any"})
    try:
        pages = get(f"https://{lang}.wikipedia.org/w/api.php?{q}").get("query", {}).get("pages", {})
    except Exception as e:
        print("  wiki error", lang, query, e); return ""
    for p in sorted(pages.values(), key=lambda p: p.get("index", 99)):
        src = (p.get("original") or {}).get("source", "") or (p.get("thumbnail") or {}).get("source", "")
        if re.search(r"\.(jpe?g|png|webp)$", src, re.I):
            print(f"  {lang}wiki '{query}' -> {p.get('title')}")
            return src
    print(f"  {lang}wiki '{query}': no image on", [p.get("title") for p in pages.values()])
    return ""


def imdb(query, year=None):
    """IMDb's public search-suggestion feed (no key); picks a film/series hit, preferring the right year."""
    key = re.sub(r"[^a-z0-9 ]", "", query.lower()).strip()
    if not key: return ""
    try:
        r = get("https://v3.sg.media-imdb.com/suggestion/x/" + urllib.parse.quote(key) + ".json").get("d", [])
    except Exception as e:
        print("  imdb error", query, e); return ""
    hits = [h for h in r if h.get("i", {}).get("imageUrl") and h.get("id", "").startswith("tt")]
    if year: hits.sort(key=lambda h: (h.get("y") != year, h.get("qid") not in ("movie", "tvMovie")))
    if hits:
        h = hits[0]; print(f"  imdb '{query}' -> {h.get('l')} ({h.get('y')})")
        return re.sub(r"\._V1_.*\.jpg$", "._V1_SX500.jpg", h["i"]["imageUrl"])
    print(f"  imdb '{query}': nothing"); return ""


def itunes(title):
    q = urllib.parse.urlencode({"media": "movie", "entity": "movie", "limit": 1, "country": "us", "term": title})
    try:
        r = get("https://itunes.apple.com/search?" + q).get("results", [])
    except Exception as e:
        print("  itunes error", title, e); return ""
    if r and r[0].get("artworkUrl100"):
        print(f"  itunes '{title}' -> {r[0].get('trackName')}")
        return re.sub(r"/\d+x\d+bb\.", "/600x900bb.", r[0]["artworkUrl100"])
    print(f"  itunes '{title}': nothing"); return ""


def find(m):
    t, q, y = m.get("t", ""), m.get("q", ""), m.get("y")
    tries = []
    if q: tries += [lambda: imdb(q, y)]
    if not AR.search(t): tries += [lambda: imdb(t, y)]
    if AR.search(t): tries += [lambda: wiki("ar", t)]
    name = q or t
    if not AR.search(name): tries += [lambda: wiki("en", name + " film"), lambda: itunes(name)]
    else: tries += [lambda: wiki("en", name)]
    for f in tries:
        u = f()
        if u: return u
    return ""


def save(url, path):
    im = Image.open(io.BytesIO(get(url, raw=True))).convert("RGB")
    if im.width > 400:
        im = im.resize((400, round(im.height * 400 / im.width)), Image.LANCZOS)
    im.save(path, "JPEG", quality=85, optimize=True)


def main():
    d = json.load(open("data.json", encoding="utf-8"))
    mv = d.get("movies") or {}
    os.makedirs("posters", exist_ok=True)
    changed, missing = False, []
    for key in ("picks", "nf", "sh"):
        for m in mv.get(key) or []:
            t = m.get("t")
            if not t: continue
            slug = hashlib.sha1((m.get("q") or t).strip().lower().encode()).hexdigest()[:12]
            path, url = f"posters/{slug}.jpg", f"{PAGES}posters/{slug}.jpg"
            img = m.get("img", "")
            if img and not img.startswith(PAGES): continue          # someone set their own poster URL
            if img == url and os.path.exists(path): continue
            print(t)
            if not os.path.exists(path):
                src = find(m)
                if not src:
                    print("  no poster found"); missing.append(t)
                    if img: m.pop("img"); changed = True
                    continue
                try: save(src, path)
                except Exception as e:
                    print("  download failed", e); missing.append(t); continue
            m["img"] = url; changed = True
    if changed:
        with open("data.json", "w", encoding="utf-8") as f:
            json.dump(d, f, ensure_ascii=False, indent=1); f.write("\n")
    print("missing:", missing or "none")


if __name__ == "__main__":
    main()
