package com.stockbadshah.user_service.service;

import com.stockbadshah.user_service.dto.UserRequest;
import com.stockbadshah.user_service.dto.UserResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {
	private final Map<String, UserRequest> users = new ConcurrentHashMap<>();

	public UserResponse register(UserRequest request) {
		users.put(request.username().toLowerCase(), request);
		return new UserResponse(request.username(), request.email(), "REGISTERED", token(request.username()));
	}

	public UserResponse login(UserRequest request) {
		UserRequest saved = users.get(request.username().toLowerCase());
		if (saved == null || !saved.password().equals(request.password())) {
			return new UserResponse(request.username(), null, "INVALID_CREDENTIALS", null);
		}
		return new UserResponse(saved.username(), saved.email(), "LOGGED_IN", token(saved.username()));
	}

	public UserResponse profile(String username) {
		UserRequest saved = users.get(username.toLowerCase());
		if (saved == null) {
			return new UserResponse(username, null, "NOT_FOUND", null);
		}
		return new UserResponse(saved.username(), saved.email(), "FOUND", token(saved.username()));
	}

	private String token(String username) {
		return Base64.getEncoder().encodeToString(("stockbadshah:" + username).getBytes(StandardCharsets.UTF_8));
	}
}
