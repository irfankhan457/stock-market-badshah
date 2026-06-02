package com.stockbadshah.indicator_service.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CandleRequest(
		@NotNull LocalDate date,
		@NotNull BigDecimal close
) {
}
