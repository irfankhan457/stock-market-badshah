package com.stockbadshah.notification_service.dto;

public record NotificationRequest(String channel, String recipient, String subject, String message) {
}
