package com.stockbadshah.strategy_service.service;

import com.stockbadshah.strategy_service.dto.BacktestResponse;
import com.stockbadshah.strategy_service.dto.FundamentalResponse;
import com.stockbadshah.strategy_service.dto.ScannerResponse;
import com.stockbadshah.strategy_service.dto.StrategyResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

@Service
public class StrategyService {
	private final RestClient restClient;
	private final String scannerUrl;
	private final String fundamentalUrl;
	private final String backtestUrl;

	public StrategyService(
			RestClient.Builder restClientBuilder,
			@Value("${services.scanner-url}") String scannerUrl,
			@Value("${services.fundamental-url}") String fundamentalUrl,
			@Value("${services.backtest-url}") String backtestUrl) {
		this.restClient = restClientBuilder.build();
		this.scannerUrl = scannerUrl;
		this.fundamentalUrl = fundamentalUrl;
		this.backtestUrl = backtestUrl;
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
		boolean fundamentalOk = fundamentalScore >= 55;
		boolean backtestOk = successRate.compareTo(BigDecimal.valueOf(45)) >= 0 || (backtest != null && backtest.tradesTested() == 0);
		String decision = technicalOk && fundamentalOk && backtestOk ? "BUY" : "NO_BUY";
		String confidence = fundamentalScore >= 75 && successRate.compareTo(BigDecimal.valueOf(60)) >= 0 ? "HIGH" : fundamentalScore >= 55 ? "MEDIUM" : "LOW";
		String reason = "Decision combines technical signal, fundamental score, and backtest success rate.";

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

	private <T> T getOrNull(String url, String symbol, Class<T> responseType) {
		try {
			return restClient.get().uri(url, symbol).retrieve().body(responseType);
		} catch (RestClientException exception) {
			return null;
		}
	}
}
