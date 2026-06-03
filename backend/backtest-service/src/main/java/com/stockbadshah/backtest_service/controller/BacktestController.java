package com.stockbadshah.backtest_service.controller;

import com.stockbadshah.backtest_service.dto.BacktestRequest;
import com.stockbadshah.backtest_service.dto.BacktestResponse;
import com.stockbadshah.backtest_service.service.BacktestService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/backtests")
public class BacktestController {
	private final BacktestService backtestService;

	public BacktestController(BacktestService backtestService) {
		this.backtestService = backtestService;
	}

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP", "service", "backtest-service");
	}

	@GetMapping("/run/{symbol}")
	public BacktestResponse runSavedSymbol(@PathVariable String symbol) {
		return backtestService.runSavedSymbol(symbol);
	}

	@PostMapping("/run")
	public BacktestResponse run(@RequestBody BacktestRequest request) {
		return backtestService.run(request);
	}
}
