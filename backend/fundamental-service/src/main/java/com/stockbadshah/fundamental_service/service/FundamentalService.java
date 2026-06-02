package com.stockbadshah.fundamental_service.service;

import com.stockbadshah.fundamental_service.dto.FundamentalRequest;
import com.stockbadshah.fundamental_service.dto.FundamentalResponse;
import com.stockbadshah.fundamental_service.dto.StockSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;

@Service
public class FundamentalService {
	private final RestClient restClient;
	private final String stockDataUrl;

	public FundamentalService(RestClient.Builder restClientBuilder, @Value("${services.stock-data-url}") String stockDataUrl) {
		this.restClient = restClientBuilder.build();
		this.stockDataUrl = stockDataUrl;
	}

	public FundamentalResponse analyzeSavedSymbol(String symbol) {
		StockSnapshot[] rows;
		try {
			rows = restClient.get()
					.uri(stockDataUrl + "/stocks/{symbol}", symbol.toUpperCase())
					.retrieve()
					.body(StockSnapshot[].class);
		} catch (RestClientResponseException exception) {
			rows = new StockSnapshot[0];
		}

		StockSnapshot latest = Arrays.stream(rows == null ? new StockSnapshot[0] : rows)
				.filter(this::hasFundamentalValues)
				.max(Comparator.comparing(StockSnapshot::stockDate, Comparator.nullsLast(Comparator.naturalOrder())))
				.orElse(new StockSnapshot(null, symbol.toUpperCase(), null, null, null, null, null, null, null, null));

		return analyze(new FundamentalRequest(
				latest.symbol(),
				latest.marketCap(),
				latest.peRatio(),
				latest.roe(),
				latest.debtToEquity(),
				null
		));
	}

	public FundamentalResponse analyze(FundamentalRequest request) {
		int score = 0;
		score += isAtLeast(request.marketCap(), BigDecimal.valueOf(100000)) ? 20 : 0;
		score += isBetween(request.peRatio(), BigDecimal.ONE, BigDecimal.valueOf(35)) ? 20 : 0;
		score += isAtLeast(request.roe(), BigDecimal.valueOf(12)) ? 25 : 0;
		score += isAtMost(request.debtToEquity(), BigDecimal.ONE) ? 20 : 0;
		score += isAtLeast(request.profitGrowth(), BigDecimal.valueOf(8)) ? 15 : 0;

		String verdict = score >= 75 ? "STRONG" : score >= 55 ? "INVESTABLE" : score >= 35 ? "WATCHLIST" : "WEAK";
		return new FundamentalResponse(
				normalizeSymbol(request.symbol()),
				verdict,
				score,
				request.marketCap(),
				request.peRatio(),
				request.roe(),
				request.debtToEquity(),
				request.profitGrowth(),
				"Fundamental score uses market cap, PE, ROE, debt-to-equity, and profit growth."
		);
	}

	private String normalizeSymbol(String symbol) {
		return symbol == null || symbol.isBlank() ? "UNKNOWN" : symbol.toUpperCase();
	}

	private boolean isAtLeast(BigDecimal value, BigDecimal floor) {
		return value != null && value.compareTo(floor) >= 0;
	}

	private boolean isAtMost(BigDecimal value, BigDecimal ceiling) {
		return value != null && value.compareTo(ceiling) <= 0;
	}

	private boolean isBetween(BigDecimal value, BigDecimal floor, BigDecimal ceiling) {
		return value != null && value.compareTo(floor) >= 0 && value.compareTo(ceiling) <= 0;
	}

	private boolean hasFundamentalValues(StockSnapshot snapshot) {
		return snapshot.marketCap() != null
				|| snapshot.peRatio() != null
				|| snapshot.roe() != null
				|| snapshot.debtToEquity() != null;
	}
}
