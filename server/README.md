# PriceHunter server

The API serves saved products and price history. Product scraping runs in the
GitHub Actions workflow every three hours and writes to PostgreSQL, so it
continues independently of the Android app being open.

## Vercel deployment

1. Create a PostgreSQL database, for example a Supabase project. Copy its
   PostgreSQL connection string (use the Session pooler connection string if
   the direct database address is not reachable).
2. Set `DATABASE_URL` in the Vercel project's environment variables and in the
   GitHub repository's Actions secrets. Use the same connection string in both
   places; do not commit it or send it in chat.
3. Ensure the Vercel project's root directory points to `server`, then deploy.
   The API initializes its tables on startup.
4. In Vercel, disable Deployment Protection for the production deployment so
   the Android app can reach `/products` and `/health` without a Vercel login.
5. Add this workflow to the repository's default branch. GitHub Actions will
   run the first refresh at the next scheduled time; you can also start it
   manually from the Actions tab. Scheduled workflow times are UTC and may be
   delayed by GitHub.
6. Set `BASE_URL` in `app/src/main/java/com/pricehunter/app/Api.kt` to the
   public Vercel deployment URL if it changes.

The free Supabase database can pause after a week without database activity.
Opening the app accesses the database through the API and normally keeps the
project active.

## Local development

Install dependencies and run the API from this directory:

```powershell
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

Without `DATABASE_URL`, local development uses `prices.db` and starts a
three-hour scheduler. To run one refresh manually, use `python main.py`.

The API provides `GET /products`, `GET /products/{product_id}/history`, and
`GET /health`. The app's “Reload saved prices” button fetches the latest rows;
it does not start a scrape. Refreshes are run by the scheduled workflow.

## Stores and coverage

The adapters cover Sigma Computer, El Nour Tech, CompuMarts, El Badr Group,
and Maximum Hardware. Some stores may fail independently; failed stores are
reported in the workflow logs, while successfully fetched stores are saved.
