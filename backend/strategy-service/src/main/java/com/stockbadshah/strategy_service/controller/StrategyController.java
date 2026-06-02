package com.stockbadshah.strategy_service.controller;

import com.stockbadshah.strategy_service.dto.StrategyResponse;
import com.stockbadshah.strategy_service.dto.UniverseRecommendationResponse;
import com.stockbadshah.strategy_service.service.StrategyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/strategy")
@CrossOrigin(origins = "*")
public class StrategyController {
	private final StrategyService strategyService;

	public StrategyController(StrategyService strategyService) {
		this.strategyService = strategyService;
	}

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP", "service", "strategy-service");
	}

	@GetMapping("/evaluate/{symbol}")
	public StrategyResponse evaluate(@PathVariable String symbol) {
		return strategyService.evaluate(symbol);
	}

	@GetMapping("/recommendations/nifty100")
	public List<StrategyResponse> scanNifty100Recommendations() {
		return strategyService.scanNifty100Recommendations();
	}

	@GetMapping("/recommendations/universe/{universe}")
	public UniverseRecommendationResponse scanUniverseRecommendations(@PathVariable String universe) {
		return strategyService.scanUniverseRecommendations(universe);
	}
}
