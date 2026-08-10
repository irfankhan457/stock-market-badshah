package com.stockbadshah.stock_data_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "stocks",
        indexes = @Index(name = "idx_stocks_symbol_stock_date", columnList = "symbol, stock_date")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;

    private String companyName;

    private BigDecimal currentPrice;

    private BigDecimal marketCap;

    private BigDecimal peRatio;

    private BigDecimal roe;

    private BigDecimal debtToEquity;

    private BigDecimal volume;

    private LocalDate stockDate;
}
