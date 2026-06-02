package com.stockbadshah.backtest_service.service;

import com.stockbadshah.backtest_service.dto.BacktestRequest;
import com.stockbadshah.backtest_service.dto.BacktestResponse;
import com.stockbadshah.backtest_service.dto.CandleRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class BacktestService {
	private final RestClient restClient;
	private final String stockDataUrl;

	public BacktestService(RestClient.Builder restClientBuilder, @Value("${services.stock-data-url}") String stockDataUrl) {
		this.restClient = restClientBuilder.build();
		this.stockDataUrl = stockDataUrl;
	}

	public BacktestResponse runSavedSymbol(String symbol) {
		CandleRequest[] candles;
		try {
			candles = restClient.get()
					.uri(stockDataUrl + "/stocks/{symbol}/candles", symbol.toUpperCase())
					.retrieve()
					.body(CandleRequest[].class);
		} catch (RestClientResponseException exception) {
			candles = new CandleRequest[0];
		}
		return run(new BacktestRequest(symbol, candles == null ? List.of() : Arrays.asList(candles)));
	}

	public BacktestResponse run(BacktestRequest request) {
		List<CandleRequest> candles = request.candles() == null ? List.of() : request.candles().stream()
				.sorted(Comparator.comparing(CandleRequest::date))
				.toList();

		int wins = 0;
		int losses = 0;
		for (int i = 0; i < Math.max(0, candles.size() - 5); i++) {
			BigDecimal entry = candles.get(i).close();
			BigDecimal target = entry.multiply(BigDecimal.valueOf(1.08));
			BigDecimal stopLoss = entry.multiply(BigDecimal.valueOf(0.95));
			for (int j = i + 1; j <= i + 5 && j < candles.size(); j++) {
				BigDecimal close = candles.get(j).close();
				if (close.compareTo(target) >= 0) {
					wins++;
					break;
				}
				if (close.compareTo(stopLoss) <= 0) {
					losses++;
					break;
				}
			}
		}

		int tested = wins + losses;
		BigDecimal successRate = tested == 0
				? BigDecimal.ZERO
				: BigDecimal.valueOf(wins).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(tested), 2, RoundingMode.HALF_UP);
		String verdict = successRate.compareTo(BigDecimal.valueOf(60)) >= 0 ? "GOOD" : successRate.compareTo(BigDecimal.valueOf(45)) >= 0 ? "AVERAGE" : "WEAK";
		return new BacktestResponse(normalizeSymbol(request.symbol()), tested, wins, losses, successRate, verdict, "Backtest checks 8 percent target and 5 percent stop loss over a 5-candle window.");
	}

	private String normalizeSymbol(String symbol) {
		return symbol == null || symbol.isBlank() ? "UNKNOWN" : symbol.toUpperCase();
	}
}
