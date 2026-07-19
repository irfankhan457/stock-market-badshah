package com.stockbadshah.api_gateway;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
class ApiGatewayApplicationTests {

	@Autowired
	private Environment environment;

	@Test
	void contextLoads() {
	}

	@Test
	void keepsLongRunningUniverseRefreshConnectionOpen() {
		Duration readTimeout = environment.getProperty("spring.http.clients.read-timeout", Duration.class);

		assertThat(readTimeout).isEqualTo(Duration.ofMinutes(16));
	}

}
