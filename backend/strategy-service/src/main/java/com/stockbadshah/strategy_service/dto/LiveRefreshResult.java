package com.stockbadshah.strategy_service.dto;

public record LiveRefreshResult(String symbol, boolean loaded, int rowsSaved, String message) {
}
