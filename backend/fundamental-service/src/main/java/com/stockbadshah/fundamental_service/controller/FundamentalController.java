package com.stockbadshah.fundamental_service.controller;

import com.stockbadshah.fundamental_service.dto.FundamentalRequest;
import com.stockbadshah.fundamental_service.dto.FundamentalResponse;
import com.stockbadshah.fundamental_service.service.FundamentalService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/fundamentals")
public class FundamentalController {
	private final FundamentalService fundamentalService;

	public FundamentalController(FundamentalService fundamentalService) {
		this.fundamentalService = fundamentalService;
	}

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP", "service", "fundamental-service");
	}

	@GetMapping("/analyze/{symbol}")
	public FundamentalResponse analyzeSavedSymbol(@PathVariable String symbol) {
		return fundamentalService.analyzeSavedSymbol(symbol);
	}

	@PostMapping("/analyze")
	public FundamentalResponse analyze(@RequestBody FundamentalRequest request) {
		return fundamentalService.analyze(request);
	}
}
