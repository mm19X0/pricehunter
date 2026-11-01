import sqlite3
import threading
import time
from contextlib import asynccontextmanager, closing

from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import FastAPI

from stores import ADAPTERS

DB = "prices.db"


def conn():
    c = sqlite3.connect(DB)
    c.row_factory = sqlite3.Row
    return c


def init_db():
    with closing(conn()) as c:
        c.executescript("""
        CREATE TABLE IF NOT EXISTS products(
            id TEXT PRIMARY KEY, name TEXT, brand TEXT, category TEXT, store_id TEXT,
            price INTEGER, old_price INTEGER, image_url TEXT, in_stock INTEGER, url TEXT, updated_at INTEGER);
        CREATE TABLE IF NOT EXISTS price_history(product_id TEXT, price INTEGER, ts INTEGER);
        CREATE INDEX IF NOT EXISTS idx_hist ON price_history(product_id, ts);
        """)


def upsert(items):
    now = int(time.time())
    with closing(conn()) as c:
        for p in items:
            last = c.execute("SELECT price FROM price_history WHERE product_id=? ORDER BY ts DESC LIMIT 1",
                             (p["id"],)).fetchone()
            if last is None or last["price"] != p["price"]:
                c.execute("INSERT INTO price_history VALUES(?,?,?)", (p["id"], p["price"], now))
            # If the store gives no old price, use the highest price seen in the last 30 days.
            hi = c.execute("SELECT MAX(price) m FROM price_history WHERE product_id=? AND ts>?",
                           (p["id"], now - 30 * 86400)).fetchone()["m"]
            old = p.get("oldPrice") or (hi if hi and hi > p["price"] else None)
            c.execute("""INSERT INTO products VALUES(?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name, brand=excluded.brand,
                category=excluded.category, price=excluded.price, old_price=excluded.old_price,
                image_url=excluded.image_url, in_stock=excluded.in_stock, url=excluded.url,
                updated_at=excluded.updated_at""",
                      (p["id"], p["name"], p["brand"], p["category"], p["storeId"], p["price"], old,
                       p.get("imageUrl"), int(p.get("inStock", True)), p["url"], now))
        c.commit()


def refresh():
    for store_id, fetch in ADAPTERS.items():
        try:
            items = fetch()
            upsert(items)
            print(f"[{store_id}] saved {len(items)} products")
        except Exception as e:  # one broken store must not stop the others
            print(f"[{store_id}] failed: {e}")


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    scheduler = BackgroundScheduler()
    scheduler.add_job(refresh, "interval", hours=3)
    scheduler.start()
    threading.Thread(target=refresh, daemon=True).start()  # first fetch at startup
    yield
    scheduler.shutdown()


app = FastAPI(title="PriceHunter API", lifespan=lifespan)


@app.get("/health")
def health():
    return {"ok": True}


@app.get("/products")
def products(q: str = "", limit: int = 3000):
    sql, args = "SELECT * FROM products", []
    if q.strip():
        sql += " WHERE LOWER(name || ' ' || brand || ' ' || category) LIKE ?"
        args.append(f"%{q.strip().lower()}%")
    sql += " LIMIT ?"
    args.append(limit)
    with closing(conn()) as c:
        rows = c.execute(sql, args).fetchall()
    return [{
        "id": r["id"], "name": r["name"], "brand": r["brand"], "category": r["category"],
        "storeId": r["store_id"], "price": r["price"], "oldPrice": r["old_price"],
        "imageUrl": r["image_url"], "inStock": bool(r["in_stock"]), "url": r["url"],
    } for r in rows]


@app.get("/products/{product_id}/history")
def history(product_id: str):
    with closing(conn()) as c:
        rows = c.execute("SELECT price, ts FROM price_history WHERE product_id=? ORDER BY ts",
                         (product_id,)).fetchall()
    return [{"price": r["price"], "ts": r["ts"]} for r in rows]


@app.post("/refresh")
def manual_refresh():
    threading.Thread(target=refresh, daemon=True).start()
    return {"started": True}
