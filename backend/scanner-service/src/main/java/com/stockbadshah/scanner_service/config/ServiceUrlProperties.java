package com.stockbadshah.scanner_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
public record ServiceUrlProperties(
		String stockDataUrl,
		String indicatorUrl
) {
}
