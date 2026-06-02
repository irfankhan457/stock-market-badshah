package com.stockbadshah.strategy_service.service;

import com.stockbadshah.strategy_service.dto.BacktestResponse;
import com.stockbadshah.strategy_service.dto.FundamentalResponse;
import com.stockbadshah.strategy_service.dto.ScannerResponse;
import com.stockbadshah.strategy_service.dto.StrategyResponse;
import com.stockbadshah.strategy_service.dto.UniverseRecommendationResponse;
import com.stockbadshah.strategy_service.dto.UniverseRefreshResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
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
		this.restClient = restClientBuilder.build();
		this.scannerUrl = scannerUrl;
		this.fundamentalUrl = fundamentalUrl;
		this.backtestUrl = backtestUrl;
		this.stockDataUrl = stockDataUrl;
	}

	public StrategyResponse evaluate(String symbol) {
		String normalized = symbol.toUpperCase();
		ScannerResponse scanner = getOrNull(scannerUrl + "/scanner/scan/{symbol}", normalized, ScannerResponse.class);
		FundamentalResponse fundamental = getOrNull(fundamentalUrl + "/fundamentals/analyze/{symbol}", normalized, FundamentalResponse.class);
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
		String reason = "Pass rule: price trend must be positive and past success rate must be at least 20 percent.";

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

		UniverseRefreshResult refreshResult = restClient.post()
				.uri(stockDataUrl + "/stocks/live/universe/{universe}/refresh", normalizedUniverse)
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
				.map(this::evaluate)
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
			return evaluate(symbol);
		} catch (RestClientException exception) {
			return evaluate(symbol);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private boolean hasFundamentalValues(FundamentalResponse fundamental) {
		return fundamental.marketCap() != null
				|| fundamental.peRatio() != null
				|| fundamental.roe() != null
				|| fundamental.debtToEquity() != null
				|| fundamental.profitGrowth() != null;
	}

	private <T> T getOrNull(String url, String symbol, Class<T> responseType) {
		try {
			return restClient.get().uri(url, symbol).retrieve().body(responseType);
		} catch (RestClientException exception) {
			return null;
		}
	}
}
