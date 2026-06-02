package com.stockbadshah.strategy_service.controller;

import com.stockbadshah.strategy_service.dto.StrategyResponse;
import com.stockbadshah.strategy_service.service.StrategyService;
import org.springframework.web.bind.annotation.*;

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
}
