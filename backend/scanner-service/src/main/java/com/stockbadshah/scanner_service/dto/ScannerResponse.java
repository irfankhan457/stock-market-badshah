package com.stockbadshah.scanner_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScannerResponse(
		String symbol,
		String signal,
		LocalDate buyDate,
		BigDecimal buyPrice,
		BigDecimal rsi,
		BigDecimal sma20,
		BigDecimal target,
		BigDecimal stopLoss,
		String result,
		int candlesCount,
		String source
) {
	public static ScannerResponse fromIndicator(IndicatorResponse indicator, int candlesCount, String source) {
		return new ScannerResponse(
				indicator.symbol(),
				indicator.signal(),
				indicator.buyDate(),
				indicator.buyPrice(),
				indicator.rsi(),
				indicator.sma20(),
				indicator.target(),
				indicator.stopLoss(),
				indicator.result(),
				candlesCount,
				source
		);
	}
}
