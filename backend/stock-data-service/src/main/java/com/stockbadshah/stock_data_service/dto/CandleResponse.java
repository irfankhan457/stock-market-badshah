package com.stockbadshah.stock_data_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CandleResponse(
        LocalDate date,
        BigDecimal close
) {
}
