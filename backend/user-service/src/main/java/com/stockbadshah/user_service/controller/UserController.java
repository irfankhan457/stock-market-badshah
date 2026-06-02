package com.stockbadshah.user_service.controller;

import com.stockbadshah.user_service.dto.UserRequest;
import com.stockbadshah.user_service.dto.UserResponse;
import com.stockbadshah.user_service.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "*")
public class UserController {
	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP", "service", "user-service");
	}

	@PostMapping("/register")
	public UserResponse register(@RequestBody UserRequest request) {
		return userService.register(request);
	}

	@PostMapping("/login")
	public UserResponse login(@RequestBody UserRequest request) {
		return userService.login(request);
	}

	@GetMapping("/{username}")
	public UserResponse profile(@PathVariable String username) {
		return userService.profile(username);
	}
}
