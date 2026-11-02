import json
import os
import sqlite3
import sys
import threading
import time
from contextlib import asynccontextmanager, closing

import psycopg
from psycopg.rows import dict_row
from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import FastAPI, Query

from stores import ADAPTERS

DB = os.environ.get("PRICEHUNTER_DB", "prices.db")
DATABASE_URL = os.environ.get("DATABASE_URL")


def conn():
    if DATABASE_URL:
        return psycopg.connect(DATABASE_URL, row_factory=dict_row)
    if os.environ.get("VERCEL"):
        raise RuntimeError("DATABASE_URL must be configured for Vercel deployments.")
    c = sqlite3.connect(DB)
    c.row_factory = sqlite3.Row
    return c


def execute(connection, query, args=()):
    if not DATABASE_URL:
        query = query.replace("%s", "?")
    return connection.execute(query, args)


def init_db():
    with closing(conn()) as c:
        execute(c, """
        CREATE TABLE IF NOT EXISTS products(
            id TEXT PRIMARY KEY, name TEXT, brand TEXT, category TEXT, store_id TEXT,
            price INTEGER, old_price INTEGER, image_url TEXT, in_stock BOOLEAN, url TEXT,
            updated_at INTEGER, specifications TEXT);
        """)
        execute(c, """
        CREATE TABLE IF NOT EXISTS price_history(
            product_id TEXT, price INTEGER, ts BIGINT);
        """)
        execute(c, "CREATE INDEX IF NOT EXISTS idx_hist ON price_history(product_id, ts)")
        if DATABASE_URL:
            execute(c, "ALTER TABLE products ADD COLUMN IF NOT EXISTS specifications TEXT")
        else:
            columns = {row["name"] for row in c.execute("PRAGMA table_info(products)")}
            if "specifications" not in columns:
                c.execute("ALTER TABLE products ADD COLUMN specifications TEXT")
        c.commit()


def upsert(items):
    now = int(time.time())
    with closing(conn()) as c:
        for p in items:
            last = execute(
                c,
                "SELECT price FROM price_history WHERE product_id=%s ORDER BY ts DESC LIMIT 1",
                (p["id"],),
            ).fetchone()
            if last is None or last["price"] != p["price"]:
                execute(c, "INSERT INTO price_history VALUES(%s,%s,%s)",
                        (p["id"], p["price"], now))
            # If the store gives no old price, use the highest price seen in the last 30 days.
            hi = execute(
                c,
                "SELECT MAX(price) AS m FROM price_history WHERE product_id=%s AND ts>%s",
                (p["id"], now - 30 * 86400),
            ).fetchone()["m"]
            old = p.get("oldPrice") or (hi if hi and hi > p["price"] else None)
            specifications = p.get("specifications")
            execute(c, """INSERT INTO products
                (id, name, brand, category, store_id, price, old_price, image_url,
                 in_stock, url, updated_at, specifications)
                VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name, brand=excluded.brand,
                category=excluded.category, price=excluded.price, old_price=excluded.old_price,
                image_url=excluded.image_url, in_stock=excluded.in_stock, url=excluded.url,
                updated_at=excluded.updated_at,
                specifications=COALESCE(excluded.specifications, products.specifications)""",
                      (p["id"], p["name"], p["brand"], p["category"], p["storeId"], p["price"], old,
                       p.get("imageUrl"), bool(p.get("inStock", True)), p["url"], now,
                       json.dumps(specifications, ensure_ascii=False) if specifications else None))
        c.commit()


def refresh():
    failures = []
    for store_id, fetch in ADAPTERS.items():
        try:
            items = fetch()
            upsert(items)
            print(f"[{store_id}] saved {len(items)} products")
        except Exception as e:  # one broken store must not stop the others
            print(f"[{store_id}] failed: {e}")
            failures.append(store_id)
    return failures


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    scheduler = None
    if not DATABASE_URL:
        scheduler = BackgroundScheduler()
        scheduler.add_job(refresh, "interval", hours=3)
        scheduler.start()
        threading.Thread(target=refresh, daemon=True).start()
    yield
    if scheduler:
        scheduler.shutdown()


app = FastAPI(title="PriceHunter API", lifespan=lifespan)


@app.get("/health")
def health():
    with closing(conn()) as c:
        execute(c, "SELECT 1")
    return {"ok": True}


@app.get("/products")
def products(
    q: str = "",
    limit: int = Query(default=3000, ge=1, le=3000),
    offset: int = Query(default=0, ge=0, le=1000000),
):
    sql, args = "SELECT * FROM products", []
    if q.strip():
        sql += " WHERE LOWER(name || ' ' || brand || ' ' || category) LIKE %s"
        args.append(f"%{q.strip().lower()}%")
    sql += " ORDER BY id LIMIT %s OFFSET %s"
    args.extend((limit, offset))
    with closing(conn()) as c:
        rows = execute(c, sql, args).fetchall()
    return [{
        "id": r["id"], "name": r["name"], "brand": r["brand"], "category": r["category"],
        "storeId": r["store_id"], "price": r["price"], "oldPrice": r["old_price"],
        "imageUrl": r["image_url"], "inStock": bool(r["in_stock"]), "url": r["url"],
        "specifications": json.loads(r["specifications"] or "{}"),
    } for r in rows]


@app.get("/products/{product_id}/history")
def history(product_id: str):
    with closing(conn()) as c:
        rows = execute(
            c,
            "SELECT price, ts FROM price_history WHERE product_id=%s ORDER BY ts",
            (product_id,),
        ).fetchall()
    return [{"price": r["price"], "ts": r["ts"]} for r in rows]


if __name__ == "__main__":
    init_db()
    failed_stores = refresh()
    if failed_stores:
        print(f"Refresh failed for stores: {', '.join(failed_stores)}")
        sys.exit(1)
