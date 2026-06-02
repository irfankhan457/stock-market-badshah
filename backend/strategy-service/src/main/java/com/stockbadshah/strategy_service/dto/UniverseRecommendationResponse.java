package com.stockbadshah.strategy_service.dto;

import java.util.List;

public record UniverseRecommendationResponse(
		String universe,
		int total,
		int loaded,
		int failed,
		int passed,
		List<String> failedSymbols,
		List<StrategyResponse> recommendations
) {
}
