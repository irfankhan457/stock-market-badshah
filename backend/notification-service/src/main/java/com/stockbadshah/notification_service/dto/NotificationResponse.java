package com.stockbadshah.notification_service.dto;

import java.time.LocalDateTime;

public record NotificationResponse(String channel, String recipient, String status, String message, LocalDateTime createdAt) {
}
