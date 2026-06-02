package com.stockbadshah.fundamental_service.dto;

import java.math.BigDecimal;

public record FundamentalResponse(
		String symbol,
		String verdict,
		int score,
		BigDecimal marketCap,
		BigDecimal peRatio,
		BigDecimal pegRatio,
		BigDecimal roe,
		BigDecimal debtToEquity,
		BigDecimal profitGrowth,
		BigDecimal salesGrowth,
		BigDecimal salesCagr,
		BigDecimal profitCagr,
		BigDecimal stockPriceCagr,
		BigDecimal netProfit,
		String futurePerspective,
		String orderBook,
		String dataSource,
		String summary
) {
}
