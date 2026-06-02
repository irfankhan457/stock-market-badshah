package com.stockbadshah.indicator_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IndicatorResponse(
		String symbol,
		String signal,
		LocalDate buyDate,
		BigDecimal buyPrice,
		BigDecimal rsi,
		BigDecimal sma20,
		BigDecimal target,
		BigDecimal stopLoss,
		String result
) {
}
