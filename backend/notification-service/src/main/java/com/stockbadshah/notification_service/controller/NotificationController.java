package com.stockbadshah.notification_service.controller;

import com.stockbadshah.notification_service.dto.NotificationRequest;
import com.stockbadshah.notification_service.dto.NotificationResponse;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {
	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP", "service", "notification-service");
	}

	@PostMapping("/send")
	public NotificationResponse send(@RequestBody NotificationRequest request) {
		return new NotificationResponse(request.channel(), request.recipient(), "QUEUED", request.message(), LocalDateTime.now());
	}

	@GetMapping("/test/{channel}")
	public NotificationResponse test(@PathVariable String channel) {
		return new NotificationResponse(channel.toUpperCase(), "demo-user", "QUEUED", "Test alert is ready for integration.", LocalDateTime.now());
	}
}
