# Steps 16 To 20

## Step 16: Add indicator-service

Created `backend/indicator-service` as a new Spring Boot microservice.

- Port: `8082`
- Eureka service name: `indicator-service`
- Eureka URL: `http://localhost:8761/eureka/`

## Step 17: Add indicator calculation API

Use this direct endpoint:

```http
POST http://localhost:8082/indicators/calculate
```

Use this body:

```json
{
  "symbol": "RELIANCE",
  "candles": [
    { "date": "2026-05-10", "close": 2800 },
    { "date": "2026-05-11", "close": 2810 },
    { "date": "2026-05-12", "close": 2820 },
    { "date": "2026-05-13", "close": 2790 },
    { "date": "2026-05-14", "close": 2785 },
    { "date": "2026-05-15", "close": 2770 },
    { "date": "2026-05-16", "close": 2760 },
    { "date": "2026-05-17", "close": 2750 },
    { "date": "2026-05-18", "close": 2740 },
    { "date": "2026-05-19", "close": 2730 },
    { "date": "2026-05-20", "close": 2720 },
    { "date": "2026-05-21", "close": 2710 },
    { "date": "2026-05-22", "close": 2700 },
    { "date": "2026-05-23", "close": 2690 },
    { "date": "2026-05-24", "close": 2710 },
    { "date": "2026-05-25", "close": 2730 },
    { "date": "2026-05-26", "close": 2750 },
    { "date": "2026-05-27", "close": 2770 },
    { "date": "2026-05-28", "close": 2790 },
    { "date": "2026-05-29", "close": 2810 }
  ]
}
```

The response includes signal, buy date, buy price, RSI, SMA 20, target, stop loss, and result.

## Step 18: Fix gateway dependency conflict

The API Gateway now uses only Spring Cloud Gateway Server MVC. The old WebFlux starter dependency was removed so MVC routing is not mixed with WebFlux routing.

Gateway indicator endpoint:

```http
POST http://localhost:8080/indicators/calculate
```

Gateway stock endpoint:

```http
GET http://localhost:8080/stocks
```

## Step 19: Correct startup order

Start services in this exact order:

1. `discovery-server`
2. `stock-data-service`
3. `indicator-service`
4. `api-gateway`

Then open Eureka:

```text
http://localhost:8761
```

You should see:

- `STOCK-DATA-SERVICE`
- `INDICATOR-SERVICE`
- `API-GATEWAY`

## Step 20: Test everything

Direct stock service:

```http
GET http://localhost:8081/stocks
```

Gateway stock route:

```http
GET http://localhost:8080/stocks
```

Direct indicator service:

```http
GET http://localhost:8082/indicators/health
```

Gateway indicator route:

```http
GET http://localhost:8080/indicators/health
```
