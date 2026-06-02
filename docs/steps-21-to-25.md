# Steps 21 To 25

## Step 21: Add stock history lookup

The stock-data-service now supports fetching all rows for one symbol:

```http
GET http://localhost:8080/stocks/RELIANCE
```

This returns saved stock rows ordered by `stockDate`.

## Step 22: Add candle API

The stock-data-service now converts saved stock rows into indicator-ready candles:

```http
GET http://localhost:8080/stocks/RELIANCE/candles
```

Response format:

```json
[
  { "date": "2026-05-10", "close": 2800 }
]
```

## Step 23: Add bulk candle save

Use this to save many date/price rows at once:

```http
POST http://localhost:8080/stocks/bulk
```

Body:

```json
[
  {
    "symbol": "RELIANCE",
    "companyName": "Reliance",
    "currentPrice": 2800,
    "stockDate": "2026-05-10"
  }
]
```

## Step 24: Add simple frontend scanner

Open this file in your browser:

```text
D:\StockMarketBadsaha\frontend\index.html
```

From the page you can:

- load sample candles
- save candles into PostgreSQL through the gateway
- calculate signal, buy date, buy price, RSI, SMA 20, target, and stop loss
- see saved stock rows

## Step 25: Test full flow

Start services:

1. `discovery-server`
2. `stock-data-service`
3. `indicator-service`
4. `api-gateway`

Then test:

1. Open `http://localhost:8761`
2. Confirm `STOCK-DATA-SERVICE`, `INDICATOR-SERVICE`, and `API-GATEWAY`
3. Open `frontend/index.html`
4. Click `Save Candles`
5. Click `Scan Signal`

If the page says to start backend services, it means one of the four services is not running or the gateway is not ready yet.
