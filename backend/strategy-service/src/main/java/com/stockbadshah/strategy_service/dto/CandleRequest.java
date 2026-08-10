package com.stockbadshah.strategy_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CandleRequest(LocalDate date, BigDecimal close) {
}
