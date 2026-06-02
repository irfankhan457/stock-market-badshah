package com.stockbadshah.strategy_service.dto;

import java.math.BigDecimal;

public record FundamentalResponse(String symbol, String verdict, int score, BigDecimal marketCap, BigDecimal peRatio, BigDecimal roe, BigDecimal debtToEquity, BigDecimal profitGrowth, String summary) {
}
