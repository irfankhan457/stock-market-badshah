package com.stockbadshah.stock_data_service.dto;

public record LiveRefreshResult(String symbol, boolean loaded, int rowsSaved, String message) {
}
