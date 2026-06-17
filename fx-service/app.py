import os
import redis
import requests
from flask import Flask, jsonify, request
from dotenv import load_dotenv

# Load .env file
load_dotenv()

app = Flask(__name__)

# ── Redis connection ──────────────────────────────────────────────────────────
cache = redis.Redis(
    host=os.getenv("REDIS_HOST", "localhost"),
    port=int(os.getenv("REDIS_PORT", 6379)),
    password=os.getenv("REDIS_PASSWORD", None),
    decode_responses=True  # returns strings not bytes
)

FX_API_KEY = os.getenv("FX_API_KEY")
CACHE_TTL  = 60  # cache rates for 60 seconds


# ── Helper: fetch rate from external API ─────────────────────────────────────
def fetch_live_rate(from_currency, to_currency):
    url = f"https://v6.exchangerate-api.com/v6/{FX_API_KEY}/pair/{from_currency}/{to_currency}"
    response = requests.get(url, timeout=5)
    data = response.json()

    if data.get("result") != "success":
        raise Exception(f"FX API error: {data.get('error-type', 'unknown')}")

    return data["conversion_rate"]


# ── Helper: get rate (cache first, API on miss) ───────────────────────────────
def get_rate(from_currency, to_currency):
    cache_key = f"fx:{from_currency}:{to_currency}"

    # 1. Check Redis cache
    cached = cache.get(cache_key)
    if cached:
        return float(cached), "cache"

    # 2. Cache miss — call live API
    rate = fetch_live_rate(from_currency, to_currency)

    # 3. Store in Redis with 60s TTL
    cache.setex(cache_key, CACHE_TTL, str(rate))

    return rate, "live"


# ── Routes ────────────────────────────────────────────────────────────────────

# Health check — Docker and other services use this
@app.route("/health")
def health():
    return jsonify({"status": "ok", "service": "fx-service"})


# Get exchange rate for a currency pair
@app.route("/rates/<from_currency>/<to_currency>")
def get_exchange_rate(from_currency, to_currency):
    # Uppercase the currencies just in case
    from_c = from_currency.upper()
    to_c   = to_currency.upper()

    # Same currency — rate is always 1
    if from_c == to_c:
        return jsonify({
            "from": from_c,
            "to":   to_c,
            "rate": 1.0,
            "source": "identity"
        })

    try:
        rate, source = get_rate(from_c, to_c)
        return jsonify({
            "from":   from_c,
            "to":     to_c,
            "rate":   rate,
            "source": source  # "cache" or "live"
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500


# Convert an amount between currencies
@app.route("/rates/convert", methods=["POST"])
def convert():
    body = request.get_json()

    # Validate input
    if not body or "amount" not in body or "from" not in body or "to" not in body:
        return jsonify({"error": "amount, from, and to are required"}), 400

    amount    = float(body["amount"])
    from_c    = body["from"].upper()
    to_c      = body["to"].upper()

    if from_c == to_c:
        return jsonify({
            "original_amount":  amount,
            "converted_amount": amount,
            "rate": 1.0,
            "from": from_c,
            "to":   to_c
        })

    try:
        rate, source = get_rate(from_c, to_c)
        converted    = round(amount * rate, 8)

        return jsonify({
            "original_amount":  amount,
            "converted_amount": converted,
            "rate":             rate,
            "from":             from_c,
            "to":               to_c,
            "source":           source
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500


# ── Start server ──────────────────────────────────────────────────────────────
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)