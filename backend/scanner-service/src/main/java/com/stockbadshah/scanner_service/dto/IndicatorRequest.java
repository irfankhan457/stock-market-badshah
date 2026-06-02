package com.stockbadshah.scanner_service.dto;

import java.util.List;

public record IndicatorRequest(
		String symbol,
		List<CandleRequest> candles
) {
}
