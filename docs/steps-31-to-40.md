# Steps 31 To 40

## Step 31: Add scanner-service

Created `backend/scanner-service` as a new Spring Boot microservice.

- Port: `8083`
- Eureka service name: `scanner-service`
- Purpose: orchestrate stock candles and indicator calculation

## Step 32: Add scanner health API

Gateway endpoint:

```http
GET http://localhost:8080/scanner/health
```

## Step 33: Add saved-symbol scan API

Gateway endpoint:

```http
GET http://localhost:8080/scanner/scan/RELIANCE
```

This fetches candles from `stock-data-service`, sends them to `indicator-service`, and returns one combined scanner response.

## Step 34: Add request-body scan API

Gateway endpoint:

```http
POST http://localhost:8080/scanner/scan
```

Body:

```json
{
  "symbol": "RELIANCE",
  "candles": [
    { "date": "2026-05-10", "close": 2800 },
    { "date": "2026-05-11", "close": 2810 }
  ]
}
```

## Step 35: Route scanner-service through API Gateway

Added gateway route:

```text
/scanner/**
```

## Step 36: Keep frontend API Gateway only

Angular now calls:

```http
POST http://localhost:8080/scanner/scan
```

for scan calculation.

## Step 37: Add scanner status to Angular

The Services tab now includes Scanner Service status.

## Step 38: Show scan source and candle count

The Angular scanner now shows:

- scan source
- candles used
- signal
- buy date
- buy price
- RSI
- SMA 20
- target
- stop loss

## Step 39: Update Postman collection

The Postman collection now includes Scanner Service requests under:

```text
05 - Scanner Service Via API Gateway
```

## Step 40: Startup order

Start services in this order:

1. `discovery-server`
2. `stock-data-service`
3. `indicator-service`
4. `scanner-service`
5. `api-gateway`

## Final extension: Full saved-symbol screener

Added:

```http
GET http://localhost:8080/stocks/meta/symbols
GET http://localhost:8080/scanner/screen
```

The Angular app now includes a `Screener` tab that runs `GET /scanner/screen` through the API Gateway and displays signal, buy date, price, RSI, target, stop loss, and candle count for saved symbols.
