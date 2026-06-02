# Stock Market Badshah Final Runbook

## What Is Implemented

- Eureka discovery server
- API Gateway
- Stock data microservice with PostgreSQL storage
- Indicator microservice with RSI 14, SMA 20, target, stop loss, and signal calculation
- Scanner microservice for single-stock scan and full saved-symbol screening
- Fundamental microservice for market cap, PE, ROE, debt-to-equity, profit growth, and verdict scoring
- Backtest microservice for historical target/stop-loss success-rate checks
- Strategy microservice that combines scanner, fundamentals, and backtest into BUY / NO_BUY
- User microservice with demo register/login/profile endpoints
- Notification microservice with queued demo alert endpoints for Telegram/email style integration later
- Angular 21 frontend with scanner, strategy, screener, saved stocks, and service status views
- Postman collection with API Gateway-only endpoints

## Startup Order

Start services in this order:

1. `discovery-server`
2. `stock-data-service`
3. `indicator-service`
4. `scanner-service`
5. `fundamental-service`
6. `backtest-service`
7. `strategy-service`
8. `user-service`
9. `notification-service`
10. `api-gateway`
11. Angular frontend

## Backend Ports

```text
discovery-server   8761
stock-data-service 8081
indicator-service  8082
scanner-service    8083
fundamental-service 8084
strategy-service   8085
backtest-service   8086
user-service       8087
notification-service 8088
api-gateway        8080
frontend           4200
```

## Angular Frontend

```powershell
cd D:\StockMarketBadsaha\frontend
npm start -- --host 127.0.0.1 --port 4200
```

Open:

```text
http://localhost:4200
```

## Main Gateway APIs

```http
GET  http://localhost:8080/stocks
POST http://localhost:8080/stocks
POST http://localhost:8080/stocks/bulk
GET  http://localhost:8080/stocks/meta/symbols
GET  http://localhost:8080/stocks/RELIANCE
GET  http://localhost:8080/stocks/RELIANCE/candles

GET  http://localhost:8080/indicators/health
GET  http://localhost:8080/indicators/calculate
POST http://localhost:8080/indicators/calculate

GET  http://localhost:8080/scanner/health
GET  http://localhost:8080/scanner/scan/RELIANCE
POST http://localhost:8080/scanner/scan
GET  http://localhost:8080/scanner/screen

GET  http://localhost:8080/fundamentals/health
GET  http://localhost:8080/fundamentals/analyze/RELIANCE
POST http://localhost:8080/fundamentals/analyze

GET  http://localhost:8080/backtests/health
GET  http://localhost:8080/backtests/run/RELIANCE
POST http://localhost:8080/backtests/run

GET  http://localhost:8080/strategy/health
GET  http://localhost:8080/strategy/evaluate/RELIANCE

GET  http://localhost:8080/users/health
POST http://localhost:8080/users/register
POST http://localhost:8080/users/login
GET  http://localhost:8080/users/demo

GET  http://localhost:8080/notifications/health
GET  http://localhost:8080/notifications/test/telegram
POST http://localhost:8080/notifications/send
```

## Postman

Import:

```text
D:\StockMarketBadsaha\testing\StockMarketBadshah.postman_collection.json
```

All collection requests use:

```text
http://localhost:8080
```

## Verified

- Backend Maven tests pass for stock-data-service and scanner-service after final changes.
- Backend Maven tests pass for api-gateway, fundamental-service, strategy-service, backtest-service, user-service, and notification-service.
- Angular production build passes.
- Angular unit tests pass.
- Live gateway scan returns signal, RSI, SMA 20, target, stop loss, source, and candle count.
- Gateway collection is organized by microservice and uses `http://localhost:8080` only for application APIs.
- Live Angular Screener tab runs successfully and displays saved symbol results.
- Browser console has no errors in final UI check.
