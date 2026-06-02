package com.stockbadshah.fundamental_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockSnapshot(
		Long id,
		String symbol,
		String companyName,
		BigDecimal currentPrice,
		BigDecimal marketCap,
		BigDecimal peRatio,
		BigDecimal roe,
		BigDecimal debtToEquity,
		BigDecimal volume,
		LocalDate stockDate
) {
}
