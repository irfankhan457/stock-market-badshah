package com.stockbadshah.strategy_service.dto;

import java.util.List;

public record ScannerRequest(String symbol, List<CandleRequest> candles) {
}
