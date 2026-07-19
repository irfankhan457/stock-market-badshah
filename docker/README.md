# Local PostgreSQL with Docker

This project uses the official PostgreSQL container image. The database is
stored in a named Docker volume, so normal container restarts and upgrades do
not erase stock data.

## Start the database

Make sure Docker Desktop (or another Docker-compatible engine) is running, then
run this from the repository root:

```powershell
docker compose -f docker/compose.yaml up -d
docker compose -f docker/compose.yaml ps
```

Wait until the `postgres` service reports `healthy`, then start the stock data
service:

```powershell
cd backend\stock-data-service
.\mvnw.cmd spring-boot:run
```

The default local settings are:

| Setting | Value |
| --- | --- |
| Host | `localhost` |
| Port | `5432` |
| Database | `stock_market_badshah` |
| User | `stock_app` |
| Password | `stock_app_dev_only` |

The database port is bound to `127.0.0.1`, so it is not exposed to other
machines. The default password is only for local development.

## Override local settings

Set these environment variables in the terminal before starting both Docker
Compose and the Spring service:

```powershell
$env:STOCK_DB_NAME = "stock_market_badshah"
$env:STOCK_DB_USER = "stock_app"
$env:STOCK_DB_PASSWORD = "choose-a-local-password"
$env:STOCK_DB_PORT = "5432"
```

`STOCK_DB_URL` and `STOCK_DB_HOST` can also override the JDBC URL or host for
the Spring service.

## Stop or reset

Stop the database without deleting its data:

```powershell
docker compose -f docker/compose.yaml stop
```

Remove the container while retaining the database volume:

```powershell
docker compose -f docker/compose.yaml down
```

To intentionally delete all local stock data and recreate an empty database:

```powershell
docker compose -f docker/compose.yaml down --volumes
```
