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
        "gsrlimit": 3, "prop": "pageimages", "piprop": "original", "pilicense": "any"})
    try:
        pages = get(f"https://{lang}.wikipedia.org/w/api.php?{q}").get("query", {}).get("pages", {})
    except Exception as e:
        print("  wiki error", lang, query, e); return ""
    for p in sorted(pages.values(), key=lambda p: p.get("index", 99)):
        src = (p.get("original") or {}).get("source", "")
        if re.search(r"\.(jpe?g|png|webp)$", src, re.I):
            print(f"  {lang}wiki '{query}' -> {p.get('title')}")
            return src
    return ""


def itunes(title):
    q = urllib.parse.urlencode({"media": "movie", "entity": "movie", "limit": 1, "country": "us", "term": title})
    try:
        r = get("https://itunes.apple.com/search?" + q).get("results", [])
    except Exception as e:
        print("  itunes error", title, e); return ""
    if r and r[0].get("artworkUrl100"):
        print(f"  itunes '{title}' -> {r[0].get('trackName')}")
        return re.sub(r"/\d+x\d+bb\.", "/600x900bb.", r[0]["artworkUrl100"])
    return ""


def find(m):
    t, q = m.get("t", ""), m.get("q", "")
    tries = []
    if AR.search(t):
        tries += [lambda: wiki("ar", t)]
        if q: tries += [lambda: wiki("en", q + " film"), lambda: itunes(q)]
        else: tries += [lambda: wiki("en", t)]
    else:
        name = q or t
        tries += [lambda: wiki("en", name + " film"), lambda: itunes(name), lambda: wiki("en", name)]
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
