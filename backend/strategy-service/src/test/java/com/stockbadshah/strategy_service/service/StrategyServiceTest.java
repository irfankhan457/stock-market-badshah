package com.stockbadshah.strategy_service.service;

import com.stockbadshah.strategy_service.dto.UniverseRecommendationResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyServiceTest {
	private final AtomicInteger activeCandleRequests = new AtomicInteger();
	private final AtomicInteger maximumActiveCandleRequests = new AtomicInteger();
	private final AtomicInteger candleRequests = new AtomicInteger();
	private final AtomicInteger scannerPosts = new AtomicInteger();
	private final AtomicInteger backtestPosts = new AtomicInteger();
	private final AtomicInteger unexpectedRequests = new AtomicInteger();

	private HttpServer server;
	private ExecutorService serverExecutor;
	private String baseUrl;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(0), 0);
		serverExecutor = Executors.newCachedThreadPool();
		server.setExecutor(serverExecutor);
		server.createContext("/", this::handleRequest);
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
		serverExecutor.shutdownNow();
	}

	@Test
	@Timeout(10)
	void scanReusesOneCandleFetchPerSymbolAndBoundsBlockingCalls() {
		StrategyService service = new StrategyService(
				RestClient.builder(),
				baseUrl,
				baseUrl,
				baseUrl,
				baseUrl,
				2
		);

		UniverseRecommendationResponse response = service.scanUniverseRecommendations("nifty100");

		assertEquals(5, response.total());
		assertEquals(4, response.loaded());
		assertEquals(1, response.failed());
		assertEquals(List.of("MISS"), response.failedSymbols());
		assertEquals(List.of("AAA", "BBB", "CCC", "DDD"), response.recommendations().stream()
				.map(recommendation -> recommendation.symbol())
				.toList());

		assertEquals(4, candleRequests.get(), "each symbol should load candles once");
		assertEquals(4, scannerPosts.get(), "scanner should receive the shared candle payload");
		assertEquals(4, backtestPosts.get(), "backtest should receive the shared candle payload");
		assertEquals(0, unexpectedRequests.get());
		assertTrue(maximumActiveCandleRequests.get() >= 2, "configured parallelism should be used");
		assertTrue(maximumActiveCandleRequests.get() <= 2, "configured parallelism must be respected");
	}

	private void handleRequest(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		if ("/stocks/universe/nifty100/saved-status".equals(path)) {
			respond(exchange, 200, """
					{
					  "universe":"nifty100",
					  "total":5,
					  "loaded":4,
					  "failed":1,
					  "results":[
					    {"symbol":"aaa","loaded":true,"rowsSaved":250,"message":"saved"},
					    {"symbol":"bbb","loaded":true,"rowsSaved":250,"message":"saved"},
					    {"symbol":"ccc","loaded":true,"rowsSaved":250,"message":"saved"},
					    {"symbol":"ddd","loaded":true,"rowsSaved":250,"message":"saved"},
					    {"symbol":"miss","loaded":false,"rowsSaved":0,"message":"missing"}
					  ]
					}
					""");
			return;
		}

		if (path.matches("/stocks/(AAA|BBB|CCC|DDD)/candles")) {
			candleRequests.incrementAndGet();
			int active = activeCandleRequests.incrementAndGet();
			maximumActiveCandleRequests.accumulateAndGet(active, Math::max);
			try {
				try {
					Thread.sleep(75);
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				}
				respond(exchange, 200, "[{\"date\":\"2026-07-17\",\"close\":100}]");
			} finally {
				activeCandleRequests.decrementAndGet();
			}
			return;
		}

		if ("POST".equals(exchange.getRequestMethod()) && "/scanner/scan".equals(path)) {
			String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			if (!requestBody.contains("\"candles\"")) {
				unexpectedRequests.incrementAndGet();
			}
			scannerPosts.incrementAndGet();
			respond(exchange, 200, """
					{"symbol":"TEST","signal":"BUY","buyDate":"2026-07-17","buyPrice":100,"rsi":55,
					 "sma20":95,"target":110,"stopLoss":90,"result":"passed","candlesCount":1,"source":"saved"}
					""");
			return;
		}

		if ("POST".equals(exchange.getRequestMethod()) && "/backtests/run".equals(path)) {
			String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			if (!requestBody.contains("\"candles\"")) {
				unexpectedRequests.incrementAndGet();
			}
			backtestPosts.incrementAndGet();
			respond(exchange, 200, """
					{"symbol":"TEST","tradesTested":10,"wins":5,"losses":5,"successRate":50,
					 "verdict":"PASS","summary":"test"}
					""");
			return;
		}

		unexpectedRequests.incrementAndGet();
		respond(exchange, 404, "{}");
	}

	private void respond(HttpExchange exchange, int status, String json) throws IOException {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}
}
