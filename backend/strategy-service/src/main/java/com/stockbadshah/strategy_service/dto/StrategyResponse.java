package com.stockbadshah.strategy_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StrategyResponse(
		String symbol,
		String decision,
		String confidence,
		LocalDate buyDate,
		BigDecimal buyPrice,
		BigDecimal target,
		BigDecimal stopLoss,
		BigDecimal rsi,
		String technicalSignal,
		String fundamentalVerdict,
		int fundamentalScore,
		BigDecimal backtestSuccessRate,
		String reason
) {
}
