package com.stockbadshah.scanner_service.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ServiceUrlProperties.class)
public class ScannerConfig {
}
