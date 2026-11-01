"""One function per store. Each returns a list of product dicts (camelCase keys, same as the Android app)."""
import re
import time

import httpx
from bs4 import BeautifulSoup

UA = {"User-Agent": "PriceHunterBot/0.1 (personal price comparison project)"}


def num(text):
    digits = re.sub(r"[^\d.]", "", (text or "").replace(",", ""))
    return int(float(digits)) if digits else None


def badr():
    """El Badr Group (OpenCart). Selectors are a first guess: open a category page,
    right-click a product -> Inspect, and adjust them if the list comes back empty."""
    base = "https://elbadrgroupeg.store"
    categories = {
        "cpu": "CPU", "motherboard": "Motherboard", "ram": "RAM", "hdd": "Hard Disk",
        "ssd": "SSD", "monitors": "Monitor", "vga": "Graphics Card",
        "cooling": "Cooling", "power-supply": "Power Supply",
    }
    out, seen = [], set()
    with httpx.Client(headers=UA, timeout=30, follow_redirects=True) as client:
        for slug, category in categories.items():
            for page in range(1, 21):
                r = client.get(f"{base}/{slug}", params={"page": page, "limit": 100})
                if r.status_code != 200:
                    break
                cards = BeautifulSoup(r.text, "html.parser").select(".product-layout")
                new_items = 0
                for card in cards:
                    link = card.select_one(".name a") or card.select_one("a[href]")
                    if not link:
                        continue
                    url = link["href"]
                    pid = "elbadr:" + url.rstrip("/").split("/")[-1]
                    if pid in seen:
                        continue
                    price_new = card.select_one(".price-new")
                    price = num((price_new or card.select_one(".price")).get_text(" ", strip=True)
                                if (price_new or card.select_one(".price")) else "")
                    if not price:
                        continue
                    old = card.select_one(".price-old")
                    img = card.select_one("img")
                    name = link.get_text(strip=True) or (img.get("alt") if img else "")
                    seen.add(pid)
                    new_items += 1
                    out.append({
                        "id": pid, "name": name, "brand": name.split(" ")[0] if name else "",
                        "category": category, "storeId": "elbadr", "price": price,
                        "oldPrice": num(old.get_text(" ", strip=True)) if old else None,
                        "imageUrl": (img.get("data-src") or img.get("src")) if img else None,
                        "inStock": "out of stock" not in card.get_text(" ").lower(),
                        "url": url,
                    })
                if new_items == 0:
                    break
                time.sleep(1)  # be gentle with the store's server
    return out


# TODO sigma: the site is Next.js and its data comes from api.sigma-computer.com.
#   Open sigma-computer.com in Chrome -> F12 -> Network -> Fetch/XHR, open a category,
#   and copy the request that returns the product list. Then call it here with httpx.
# TODO elnour / compumarts / maximum: check each site's platform first.
#   (elnour-tech.com blocked automated access when I tried, so it may need a different approach.)

ADAPTERS = {
    "elbadr": badr,
}
