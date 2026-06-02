package com.stockbadshah.indicator_service.controller;

import com.stockbadshah.indicator_service.dto.IndicatorRequest;
import com.stockbadshah.indicator_service.dto.IndicatorResponse;
import com.stockbadshah.indicator_service.service.IndicatorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.Map;

@RestController
@RequestMapping("/indicators")
@CrossOrigin(origins = "*")
public class IndicatorController {

	private final IndicatorService indicatorService;

	public IndicatorController(IndicatorService indicatorService) {
		this.indicatorService = indicatorService;
	}

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP", "service", "indicator-service");
	}

	@GetMapping("/calculate")
	public Map<String, Object> calculateHelp() {
		return Map.of(
				"message", "Use POST /indicators/calculate with a JSON body to calculate signal, RSI, target, and stop loss.",
				"method", "POST",
				"gatewayUrl", "http://localhost:8080/indicators/calculate",
				"directUrl", "http://localhost:8082/indicators/calculate",
				"sampleBody", Map.of(
						"symbol", "RELIANCE",
						"candles", new Object[] {
								Map.of("date", "2026-05-10", "close", 2800),
								Map.of("date", "2026-05-11", "close", 2810),
								Map.of("date", "2026-05-12", "close", 2820)
						}
				)
		);
	}

	@PostMapping("/calculate")
	public IndicatorResponse calculate(@Valid @RequestBody IndicatorRequest request) {
		return indicatorService.calculate(request);
	}
}
