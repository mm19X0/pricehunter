# PriceHunter server

    cd server
    python -m venv venv && venv\Scripts\activate      (Windows)   /   source venv/bin/activate (Linux/Mac)
    pip install -r requirements.txt
    uvicorn main:app --host 0.0.0.0 --port 8000

Test in the browser: http://localhost:8000/docs  (then POST /refresh, wait a minute, GET /products)
For the phone: put your PC's local IP in ApiConfig.BASE_URL (app/.../Api.kt). Phone and PC must be on the same Wi-Fi.
