# Steps 26 To 30

## Step 26: Convert frontend to Angular

The frontend is now an Angular 21 application in:

```text
D:\StockMarketBadsaha\frontend
```

Run it with:

```powershell
cd D:\StockMarketBadsaha\frontend
npm start
```

Default Angular URL:

```text
http://localhost:4200
```

## Step 27: Build a professional scanner dashboard

The Angular app now has:

- responsive sidebar navigation
- scanner workspace
- saved stocks table
- service health cards
- trend chart
- signal, RSI, SMA, target, and stop loss result panels

## Step 28: Use API Gateway only

Frontend requests use:

```text
http://localhost:8080
```

The frontend does not call direct service ports.

## Step 29: Connect Angular to backend APIs

Angular uses these gateway endpoints:

```http
GET  /stocks
POST /stocks/bulk
GET  /stocks/{symbol}/candles
GET  /indicators/health
POST /indicators/calculate
GET  /eureka/apps
```

## Step 30: Verify Angular build

Run:

```powershell
cd D:\StockMarketBadsaha\frontend
npm run build
```

The production build should complete successfully.
