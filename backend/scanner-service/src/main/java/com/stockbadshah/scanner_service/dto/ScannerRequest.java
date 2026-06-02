package com.stockbadshah.scanner_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ScannerRequest(
		@NotBlank String symbol,
		@Valid List<CandleRequest> candles
) {
}
