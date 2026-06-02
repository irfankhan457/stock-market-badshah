# Testing

Import this Postman collection:

```text
D:\StockMarketBadsaha\testing\StockMarketBadshah.postman_collection.json
```

All collection requests use the API Gateway URL:

```text
http://localhost:8080
```

Important:

- `GET /indicators/calculate` is only a help endpoint.
- `POST /indicators/calculate` is the real indicator calculation endpoint.
- Eureka API requests are available through the gateway at `/eureka/**`.
- For every new endpoint added in this project, add the request into this collection under the correct microservice folder.
- Do not use direct service ports in Postman unless you are debugging a service in isolation.

Startup order:

1. `discovery-server`
2. `stock-data-service`
3. `indicator-service`
4. `scanner-service`
5. `api-gateway`
