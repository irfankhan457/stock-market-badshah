package com.stockbadshah.strategy_service.dto;

import java.math.BigDecimal;

public record BacktestResponse(String symbol, int tradesTested, int wins, int losses, BigDecimal successRate, String verdict, String summary) {
}
