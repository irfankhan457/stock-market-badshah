package com.stockbadshah.strategy_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScannerResponse(String symbol, String signal, LocalDate buyDate, BigDecimal buyPrice, BigDecimal rsi, BigDecimal sma20, BigDecimal target, BigDecimal stopLoss, String result, int candlesCount, String source) {
}
