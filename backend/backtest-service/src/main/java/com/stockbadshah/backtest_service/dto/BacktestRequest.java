package com.stockbadshah.backtest_service.dto;

import java.util.List;

public record BacktestRequest(String symbol, List<CandleRequest> candles) {
}
