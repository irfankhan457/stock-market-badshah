package com.stockbadshah.scanner_service.service;

import com.stockbadshah.scanner_service.config.ServiceUrlProperties;
import com.stockbadshah.scanner_service.dto.CandleRequest;
import com.stockbadshah.scanner_service.dto.IndicatorRequest;
import com.stockbadshah.scanner_service.dto.IndicatorResponse;
import com.stockbadshah.scanner_service.dto.ScannerRequest;
import com.stockbadshah.scanner_service.dto.ScannerResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class ScannerService {

	private final RestClient restClient;
	private final ServiceUrlProperties serviceUrls;

	public ScannerService(RestClient.Builder restClientBuilder, ServiceUrlProperties serviceUrls) {
		this.restClient = restClientBuilder.build();
		this.serviceUrls = serviceUrls;
	}

	public ScannerResponse scanSavedSymbol(String symbol) {
		List<CandleRequest> candles = fetchCandles(symbol);
		return calculate(symbol, candles, "stock-data-service");
	}

	public ScannerResponse scan(ScannerRequest request) {
		List<CandleRequest> candles = request.candles() == null || request.candles().isEmpty()
				? fetchCandles(request.symbol())
				: request.candles();
		String source = request.candles() == null || request.candles().isEmpty()
				? "stock-data-service"
				: "request-body";
		return calculate(request.symbol(), candles, source);
	}

	public List<ScannerResponse> screenSavedSymbols() {
		String[] symbols = restClient.get()
				.uri(serviceUrls.stockDataUrl() + "/stocks/meta/symbols")
				.retrieve()
				.body(String[].class);

		return Arrays.stream(symbols == null ? new String[0] : symbols)
				.filter(Objects::nonNull)
				.map(this::scanSavedSymbol)
				.toList();
	}

	private List<CandleRequest> fetchCandles(String symbol) {
		CandleRequest[] candles = restClient.get()
				.uri(serviceUrls.stockDataUrl() + "/stocks/{symbol}/candles", symbol.toUpperCase())
				.retrieve()
				.body(CandleRequest[].class);

		return candles == null ? List.of() : Arrays.asList(candles);
	}

	private ScannerResponse calculate(String symbol, List<CandleRequest> candles, String source) {
		IndicatorResponse indicator = restClient.post()
				.uri(serviceUrls.indicatorUrl() + "/indicators/calculate")
				.body(new IndicatorRequest(symbol.toUpperCase(), candles))
				.retrieve()
				.body(IndicatorResponse.class);

		if (indicator == null) {
			throw new IllegalStateException("Indicator service returned an empty response.");
		}

		return ScannerResponse.fromIndicator(indicator, candles.size(), source);
	}
}
