package com.stockbadshah.strategy_service.service;

import com.stockbadshah.strategy_service.dto.BacktestResponse;
import com.stockbadshah.strategy_service.dto.FundamentalResponse;
import com.stockbadshah.strategy_service.dto.ScannerResponse;
import com.stockbadshah.strategy_service.dto.StrategyResponse;
import com.stockbadshah.strategy_service.dto.UniverseRecommendationResponse;
import com.stockbadshah.strategy_service.dto.UniverseRefreshResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class StrategyService {
	private static final BigDecimal MINIMUM_SUCCESS_RATE = BigDecimal.valueOf(20);

	private final RestClient restClient;
	private final String scannerUrl;
	private final String fundamentalUrl;
	private final String backtestUrl;
	private final String stockDataUrl;

	public StrategyService(
			RestClient.Builder restClientBuilder,
			@Value("${services.scanner-url}") String scannerUrl,
			@Value("${services.fundamental-url}") String fundamentalUrl,
			@Value("${services.backtest-url}") String backtestUrl,
			@Value("${services.stock-data-url}") String stockDataUrl) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(5));
		requestFactory.setReadTimeout(Duration.ofSeconds(150));
		this.restClient = restClientBuilder.requestFactory(requestFactory).build();
		this.scannerUrl = scannerUrl;
		this.fundamentalUrl = fundamentalUrl;
		this.backtestUrl = backtestUrl;
		this.stockDataUrl = stockDataUrl;
	}

	public StrategyResponse evaluate(String symbol) {
		return evaluate(symbol, true);
	}

	private StrategyResponse evaluateForScan(String symbol) {
		return evaluate(symbol, false);
	}

	private StrategyResponse evaluate(String symbol, boolean includeFundamentals) {
		String normalized = symbol.toUpperCase();
		ScannerResponse scanner = getOrNull(scannerUrl + "/scanner/scan/{symbol}", normalized, ScannerResponse.class);
		FundamentalResponse fundamental = includeFundamentals ? getOrNull(fundamentalUrl + "/fundamentals/analyze/{symbol}", normalized, FundamentalResponse.class) : null;
		BacktestResponse backtest = getOrNull(backtestUrl + "/backtests/run/{symbol}", normalized, BacktestResponse.class);

		String technicalSignal = scanner == null ? "WAIT" : scanner.signal();
		int fundamentalScore = fundamental == null ? 0 : fundamental.score();
		BigDecimal successRate = backtest == null ? BigDecimal.ZERO : backtest.successRate();

		boolean technicalOk = "BUY".equals(technicalSignal) || "HOLD".equals(technicalSignal);
		boolean backtestOk = successRate.compareTo(MINIMUM_SUCCESS_RATE) >= 0;
		boolean hasFundamentals = fundamental != null && hasFundamentalValues(fundamental);
		boolean fundamentalOk = !hasFundamentals || fundamentalScore >= 55;
		String decision = technicalOk && backtestOk && fundamentalOk ? "BUY" : "NO_BUY";
		String confidence = successRate.compareTo(BigDecimal.valueOf(45)) >= 0 ? "HIGH" : successRate.compareTo(MINIMUM_SUCCESS_RATE) >= 0 ? "MEDIUM" : "LOW";
		String reason = includeFundamentals
				? "Full check uses price trend, past success rate, and available company fundamentals."
				: "Scan rule: price trend must be positive and past success rate must be at least 20 percent. Company fundamentals are checked on the Full Check page.";

		return new StrategyResponse(
				normalized,
				decision,
				confidence,
				scanner == null ? null : scanner.buyDate(),
				scanner == null ? null : scanner.buyPrice(),
				scanner == null ? null : scanner.target(),
				scanner == null ? null : scanner.stopLoss(),
				scanner == null ? null : scanner.rsi(),
				technicalSignal,
				fundamental == null ? "UNKNOWN" : fundamental.verdict(),
				fundamentalScore,
				fundamental == null ? null : fundamental.marketCap(),
				fundamental == null ? null : fundamental.peRatio(),
				fundamental == null ? null : fundamental.pegRatio(),
				fundamental == null ? null : fundamental.roe(),
				fundamental == null ? null : fundamental.debtToEquity(),
				fundamental == null ? null : fundamental.profitGrowth(),
				fundamental == null ? null : fundamental.salesGrowth(),
				fundamental == null ? null : fundamental.salesCagr(),
				fundamental == null ? null : fundamental.profitCagr(),
				fundamental == null ? null : fundamental.stockPriceCagr(),
				fundamental == null ? null : fundamental.netProfit(),
				fundamental == null ? "Future view is not available right now." : fundamental.futurePerspective(),
				fundamental == null ? "Order book is not available right now." : fundamental.orderBook(),
				fundamental == null ? "Not available" : fundamental.dataSource(),
				successRate,
				reason
		);
	}

	public List<StrategyResponse> scanNifty100Recommendations() {
		return scanUniverseRecommendations("nifty100").recommendations();
	}

	public UniverseRecommendationResponse scanUniverseRecommendations(String universe) {
		String normalizedUniverse = universe == null ? "nifty100" : universe.trim().toLowerCase();
		if (!"nifty500".equals(normalizedUniverse)) {
			normalizedUniverse = "nifty100";
		}

		UniverseRefreshResult refreshResult = restClient.get()
				.uri(stockDataUrl + "/stocks/universe/{universe}/saved-status", normalizedUniverse)
				.retrieve()
				.body(UniverseRefreshResult.class);

		List<String> loadedSymbols = refreshResult == null || refreshResult.results() == null ? List.of() : refreshResult.results().stream()
				.filter(Objects::nonNull)
				.filter(result -> result.loaded() && result.symbol() != null)
				.map(result -> result.symbol().toUpperCase())
				.toList();

		List<String> failedSymbols = refreshResult == null || refreshResult.results() == null ? List.of() : refreshResult.results().stream()
				.filter(Objects::nonNull)
				.filter(result -> !result.loaded() && result.symbol() != null)
				.map(result -> result.symbol().toUpperCase())
				.toList();

		List<StrategyResponse> recommendations = loadedSymbols.parallelStream()
				.map(this::evaluateForScan)
				.filter(Objects::nonNull)
				.filter(response -> "BUY".equals(response.decision()))
				.sorted((left, right) -> right.backtestSuccessRate().compareTo(left.backtestSuccessRate()))
				.toList();

		int total = refreshResult == null ? loadedSymbols.size() + failedSymbols.size() : refreshResult.total();
		int loaded = refreshResult == null ? loadedSymbols.size() : refreshResult.loaded();
		int failed = refreshResult == null ? failedSymbols.size() : refreshResult.failed();
		return new UniverseRecommendationResponse(normalizedUniverse, total, loaded, failed, recommendations.size(), failedSymbols, recommendations);
	}

	private StrategyResponse refreshAndEvaluate(String symbol) {
		try {
			restClient.post()
					.uri(stockDataUrl + "/stocks/live/{symbol}/refresh", symbol)
					.retrieve()
					.toBodilessEntity();
			Thread.sleep(350);
			return evaluateForScan(symbol);
		} catch (RestClientException exception) {
			return evaluateForScan(symbol);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private boolean hasFundamentalValues(FundamentalResponse fundamental) {
		return fundamental.marketCap() != null
				|| fundamental.peRatio() != null
				|| fundamental.pegRatio() != null
				|| fundamental.roe() != null
				|| fundamental.debtToEquity() != null
				|| fundamental.profitGrowth() != null
				|| fundamental.salesGrowth() != null;
	}

	private <T> T getOrNull(String url, String symbol, Class<T> responseType) {
		try {
			return restClient.get().uri(url, symbol).retrieve().body(responseType);
		} catch (RestClientException exception) {
			return null;
		}
	}
}
