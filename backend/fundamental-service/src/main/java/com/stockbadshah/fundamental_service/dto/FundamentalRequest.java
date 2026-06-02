package com.stockbadshah.fundamental_service.dto;

import java.math.BigDecimal;

public record FundamentalRequest(
		String symbol,
		BigDecimal marketCap,
		BigDecimal peRatio,
		BigDecimal roe,
		BigDecimal debtToEquity,
		BigDecimal profitGrowth
) {
}
