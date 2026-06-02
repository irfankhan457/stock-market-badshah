package com.stockbadshah.fundamental_service.service;

import com.stockbadshah.fundamental_service.dto.FundamentalRequest;
import com.stockbadshah.fundamental_service.dto.FundamentalResponse;
import com.stockbadshah.fundamental_service.dto.StockSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FundamentalService {
	private final RestClient restClient;
	private final String stockDataUrl;
	private final String screenerBaseUrl;
	private final String stockAnalysisBaseUrl;

	public FundamentalService(
			RestClient.Builder restClientBuilder,
			@Value("${services.stock-data-url}") String stockDataUrl,
			@Value("${services.screener-base-url:https://www.screener.in/company}") String screenerBaseUrl,
			@Value("${services.stock-analysis-base-url:https://stockanalysis.com/quote/nse}") String stockAnalysisBaseUrl) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(6));
		requestFactory.setReadTimeout(Duration.ofSeconds(8));
		this.restClient = restClientBuilder.requestFactory(requestFactory).build();
		this.stockDataUrl = stockDataUrl;
		this.screenerBaseUrl = screenerBaseUrl;
		this.stockAnalysisBaseUrl = stockAnalysisBaseUrl;
	}

	public FundamentalResponse analyzeSavedSymbol(String symbol) {
		FundamentalResponse screenerResponse = fetchScreenerFundamentals(symbol);
		if (hasFundamentalValues(screenerResponse)) {
			return screenerResponse;
		}

		FundamentalResponse stockAnalysisResponse = fetchStockAnalysisFundamentals(symbol);
		if (hasFundamentalValues(stockAnalysisResponse)) {
			return stockAnalysisResponse;
		}

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
		score += isAtLeast(request.marketCap(), BigDecimal.valueOf(10000)) ? 15 : 0;
		score += isBetween(request.peRatio(), BigDecimal.ONE, BigDecimal.valueOf(35)) ? 20 : 0;
		score += isAtLeast(request.roe(), BigDecimal.valueOf(12)) ? 20 : 0;
		score += isAtMost(request.debtToEquity(), BigDecimal.ONE) ? 15 : 0;
		score += isAtLeast(request.profitGrowth(), BigDecimal.valueOf(8)) ? 20 : 0;

		BigDecimal pegRatio = calculatePeg(request.peRatio(), request.profitGrowth());
		score += pegRatio != null && pegRatio.compareTo(BigDecimal.valueOf(2.5)) <= 0 ? 10 : 0;

		String verdict = score >= 75 ? "STRONG" : score >= 55 ? "INVESTABLE" : score >= 35 ? "WATCHLIST" : "WEAK";
		return new FundamentalResponse(
				normalizeSymbol(request.symbol()),
				verdict,
				score,
				request.marketCap(),
				request.peRatio(),
				pegRatio,
				request.roe(),
				request.debtToEquity(),
				request.profitGrowth(),
				null,
				null,
				request.profitGrowth(),
				null,
				null,
				"Future view needs business news, management commentary, and sector outlook. It is not available from the saved price rows.",
				"Order book is not available from the saved price rows.",
				"Saved stock rows",
				"Fundamental score uses market cap, PE, PEG, ROE, debt-to-equity, and profit growth when these values are available."
		);
	}

	private FundamentalResponse fetchScreenerFundamentals(String symbol) {
		String normalized = normalizeSymbol(symbol);
		try {
			String html = restClient.get()
					.uri(screenerBaseUrl + "/" + normalized + "/consolidated/")
					.header("User-Agent", "Mozilla/5.0")
					.retrieve()
					.body(String.class);

			BigDecimal marketCap = parseTopRatio(html, "Market Cap");
			BigDecimal peRatio = parseTopRatio(html, "Stock P/E");
			BigDecimal roe = parseTopRatio(html, "ROE");
			BigDecimal salesGrowth = parseRangeValue(html, "Compounded Sales Growth", "TTM");
			BigDecimal salesCagr = parseRangeValue(html, "Compounded Sales Growth", "3 Years");
			BigDecimal profitGrowth = parseRangeValue(html, "Compounded Profit Growth", "TTM");
			BigDecimal profitCagr = parseRangeValue(html, "Compounded Profit Growth", "3 Years");
			BigDecimal stockPriceCagr = parseRangeValue(html, "Stock Price CAGR", "3 Years");
			BigDecimal netProfit = parseLatestTableValue(html, "Net Profit");
			BigDecimal pegRatio = calculatePeg(peRatio, profitGrowth);

			int score = 0;
			score += isAtLeast(marketCap, BigDecimal.valueOf(10000)) ? 15 : 0;
			score += isBetween(peRatio, BigDecimal.ONE, BigDecimal.valueOf(35)) ? 20 : 0;
			score += pegRatio != null && pegRatio.compareTo(BigDecimal.valueOf(2.5)) <= 0 ? 10 : 0;
			score += isAtLeast(roe, BigDecimal.valueOf(12)) ? 20 : 0;
			score += isAtLeast(profitGrowth, BigDecimal.valueOf(8)) ? 15 : 0;
			score += isAtLeast(salesGrowth, BigDecimal.valueOf(8)) ? 10 : 0;
			score += isAtLeast(stockPriceCagr, BigDecimal.valueOf(10)) ? 10 : 0;

			String verdict = score >= 75 ? "STRONG" : score >= 55 ? "INVESTABLE" : score >= 35 ? "WATCHLIST" : "WEAK";
			String futurePerspective = buildFuturePerspective(salesGrowth, profitGrowth, stockPriceCagr);
			return new FundamentalResponse(
					normalized,
					verdict,
					score,
					marketCap,
					peRatio,
					pegRatio,
					roe,
					null,
					profitGrowth,
					salesGrowth,
					salesCagr,
					profitCagr,
					stockPriceCagr,
					netProfit,
					futurePerspective,
					"Order book is not available from this free public data source. For project/order-book companies, this should be verified from exchange filings or investor presentations.",
					"Screener public company page",
					"Fundamental score uses public values for market cap, PE, PEG, ROE, sales growth, profit growth, and stock price CAGR."
			);
		} catch (Exception exception) {
			return new FundamentalResponse(
					normalized,
					"UNKNOWN",
					0,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					"Future view is not available because free fundamental data could not be loaded.",
					"Order book is not available because free fundamental data could not be loaded.",
					"Not available",
					"Fundamental data could not be loaded from the free source right now."
			);
		}
	}

	private FundamentalResponse fetchStockAnalysisFundamentals(String symbol) {
		String normalized = normalizeSymbol(symbol);
		try {
			String quoteHtml = restClient.get()
					.uri(stockAnalysisBaseUrl + "/" + normalized + "/")
					.header("User-Agent", "Mozilla/5.0")
					.retrieve()
					.body(String.class);
			String ratioHtml = restClient.get()
					.uri(stockAnalysisBaseUrl + "/" + normalized + "/financials/ratios/")
					.header("User-Agent", "Mozilla/5.0")
					.retrieve()
					.body(String.class);

			BigDecimal marketCap = parseStockAnalysisMoneyToCrore(parseStockAnalysisValue(quoteHtml, "Market Cap"));
			BigDecimal peRatio = parseStockAnalysisPlainValue(quoteHtml, "PE Ratio");
			BigDecimal netProfit = parseStockAnalysisMoneyToCrore(parseStockAnalysisValue(quoteHtml, "Net Income"));
			BigDecimal salesGrowth = parseStockAnalysisGrowth(quoteHtml, "Revenue \\(ttm\\)");
			BigDecimal profitGrowth = parseStockAnalysisGrowth(quoteHtml, "Net Income");
			BigDecimal roe = parseStockAnalysisRatio(ratioHtml, "Return on Equity \\(ROE\\)");
			BigDecimal debtToEquity = parseStockAnalysisRatio(ratioHtml, "Debt / Equity Ratio");
			BigDecimal pegRatio = calculatePeg(peRatio, profitGrowth);

			int score = 0;
			score += isAtLeast(marketCap, BigDecimal.valueOf(10000)) ? 15 : 0;
			score += isBetween(peRatio, BigDecimal.ONE, BigDecimal.valueOf(35)) ? 20 : 0;
			score += pegRatio != null && pegRatio.compareTo(BigDecimal.valueOf(2.5)) <= 0 ? 10 : 0;
			score += isAtLeast(roe, BigDecimal.valueOf(12)) ? 20 : 0;
			score += isAtMost(debtToEquity, BigDecimal.ONE) ? 15 : 0;
			score += isAtLeast(profitGrowth, BigDecimal.valueOf(8)) ? 10 : 0;
			score += isAtLeast(salesGrowth, BigDecimal.valueOf(8)) ? 10 : 0;

			String verdict = score >= 75 ? "STRONG" : score >= 55 ? "INVESTABLE" : score >= 35 ? "WATCHLIST" : "WEAK";
			return new FundamentalResponse(
					normalized,
					verdict,
					score,
					marketCap,
					peRatio,
					pegRatio,
					roe,
					debtToEquity,
					profitGrowth,
					salesGrowth,
					null,
					null,
					null,
					netProfit,
					buildFuturePerspective(salesGrowth, profitGrowth, null),
					"Order book is not available from this free public data source. Check exchange filings or investor presentations for order-book details.",
					"StockAnalysis public quote page",
					"Fundamental score uses public values for market cap, PE, PEG, ROE, debt-to-equity, sales growth, and profit growth."
			);
		} catch (Exception exception) {
			return new FundamentalResponse(
					normalized,
					"UNKNOWN",
					0,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					"Future view is not available because free fundamental data could not be loaded.",
					"Order book is not available because free fundamental data could not be loaded.",
					"Not available",
					"Fundamental data could not be loaded from the free fallback source right now."
			);
		}
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

	private boolean hasFundamentalValues(FundamentalResponse response) {
		return response.marketCap() != null
				|| response.peRatio() != null
				|| response.roe() != null
				|| response.profitGrowth() != null
				|| response.salesGrowth() != null;
	}

	private BigDecimal calculatePeg(BigDecimal peRatio, BigDecimal profitGrowth) {
		if (peRatio == null || profitGrowth == null || profitGrowth.compareTo(BigDecimal.ZERO) <= 0) {
			return null;
		}
		return peRatio.divide(profitGrowth, 2, RoundingMode.HALF_UP);
	}

	private BigDecimal parseTopRatio(String html, String label) {
		Matcher matcher = Pattern.compile(Pattern.quote(label) + ".*?<span class=\"number\">([^<]+)</span>", Pattern.DOTALL)
				.matcher(html == null ? "" : html);
		return matcher.find() ? parseNumber(matcher.group(1)) : null;
	}

	private BigDecimal parseRangeValue(String html, String tableHeading, String rowLabel) {
		Matcher tableMatcher = Pattern.compile(Pattern.quote(tableHeading) + "(.*?</table>)", Pattern.DOTALL)
				.matcher(html == null ? "" : html);
		if (!tableMatcher.find()) {
			return null;
		}
		Matcher valueMatcher = Pattern.compile(Pattern.quote(rowLabel) + ":\\s*</td>\\s*<td>\\s*([-0-9.,]+)%", Pattern.DOTALL)
				.matcher(tableMatcher.group(1));
		return valueMatcher.find() ? parseNumber(valueMatcher.group(1)) : null;
	}

	private BigDecimal parseLatestTableValue(String html, String rowLabel) {
		Matcher rowMatcher = Pattern.compile(Pattern.quote(rowLabel) + "\\s*</td>(.*?)</tr>", Pattern.DOTALL)
				.matcher(html == null ? "" : html);
		if (!rowMatcher.find()) {
			return null;
		}
		Matcher numberMatcher = Pattern.compile("([-0-9,]+)\\s*</td>").matcher(rowMatcher.group(1));
		BigDecimal latest = null;
		while (numberMatcher.find()) {
			latest = parseNumber(numberMatcher.group(1));
		}
		return latest;
	}

	private BigDecimal parseNumber(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(value.replace(",", "").trim());
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private String parseStockAnalysisValue(String html, String labelRegex) {
		Matcher matcher = Pattern.compile(labelRegex + ".*?</td><td[^>]*>([-0-9.,]+[BMK]?)", Pattern.DOTALL)
				.matcher(html == null ? "" : html);
		return matcher.find() ? matcher.group(1) : null;
	}

	private BigDecimal parseStockAnalysisPlainValue(String html, String labelRegex) {
		return parseNumber(parseStockAnalysisValue(html, labelRegex));
	}

	private BigDecimal parseStockAnalysisGrowth(String html, String labelRegex) {
		Matcher matcher = Pattern.compile(labelRegex + ".*?<span class=\"rg\">\\+?([-0-9.,]+)%", Pattern.DOTALL)
				.matcher(html == null ? "" : html);
		if (matcher.find()) {
			return parseNumber(matcher.group(1));
		}
		Matcher negativeMatcher = Pattern.compile(labelRegex + ".*?<span class=\"rr\">([-0-9.,]+)%", Pattern.DOTALL)
				.matcher(html == null ? "" : html);
		return negativeMatcher.find() ? parseNumber(negativeMatcher.group(1)) : null;
	}

	private BigDecimal parseStockAnalysisRatio(String html, String labelRegex) {
		Matcher matcher = Pattern.compile(labelRegex + ".*?<td class=\"[^\"]*\">([-0-9.,]+)%?</td>", Pattern.DOTALL)
				.matcher(html == null ? "" : html);
		return matcher.find() ? parseNumber(matcher.group(1)) : null;
	}

	private BigDecimal parseStockAnalysisMoneyToCrore(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String clean = value.trim().toUpperCase();
		BigDecimal number = parseNumber(clean.replace("B", "").replace("M", "").replace("K", ""));
		if (number == null) {
			return null;
		}
		if (clean.endsWith("B")) {
			return number.multiply(BigDecimal.valueOf(100));
		}
		if (clean.endsWith("M")) {
			return number.divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);
		}
		if (clean.endsWith("K")) {
			return number.divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP);
		}
		return number;
	}

	private String buildFuturePerspective(BigDecimal salesGrowth, BigDecimal profitGrowth, BigDecimal stockPriceCagr) {
		if (isAtLeast(salesGrowth, BigDecimal.valueOf(8)) && isAtLeast(profitGrowth, BigDecimal.valueOf(8))) {
			return "Business growth looks healthy because both sales and profit are growing at a good pace.";
		}
		if (isAtLeast(profitGrowth, BigDecimal.valueOf(8)) || isAtLeast(stockPriceCagr, BigDecimal.valueOf(10))) {
			return "Future view is acceptable, but sales and profit growth should be watched before taking a larger position.";
		}
		return "Future view is cautious because growth numbers are not strong enough from the free public data.";
	}
}
