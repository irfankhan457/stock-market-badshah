package com.stockbadshah.scanner_service.controller;

import com.stockbadshah.scanner_service.dto.ScannerRequest;
import com.stockbadshah.scanner_service.dto.ScannerResponse;
import com.stockbadshah.scanner_service.service.ScannerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/scanner")
@CrossOrigin(origins = "*")
public class ScannerController {

	private final ScannerService scannerService;

	public ScannerController(ScannerService scannerService) {
		this.scannerService = scannerService;
	}

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP", "service", "scanner-service");
	}

	@GetMapping("/scan/{symbol}")
	public ScannerResponse scanSavedSymbol(@PathVariable String symbol) {
		return scannerService.scanSavedSymbol(symbol);
	}

	@GetMapping("/screen")
	public List<ScannerResponse> screenSavedSymbols() {
		return scannerService.screenSavedSymbols();
	}

	@PostMapping("/scan")
	public ScannerResponse scan(@Valid @RequestBody ScannerRequest request) {
		return scannerService.scan(request);
	}
}
