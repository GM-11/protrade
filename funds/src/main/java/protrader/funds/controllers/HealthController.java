package protrader.funds.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import protrader.funds.config.ApiKeys;
import protrader.funds.config.JwtVerifier;

@RestController
public class HealthController {

	private final JdbcClient jdbc;
	private final ApiKeys apiKeys;
	private final JwtVerifier jwt;

	public HealthController(JdbcClient jdbc, ApiKeys apiKeys, JwtVerifier jwt) {
		this.jdbc = jdbc;
		this.apiKeys = apiKeys;
		this.jwt = jwt;
	}

	/** Up if the process is up. */
	@GetMapping("/healthz")
	Map<String, String> healthz() {
		return Map.of("status", "ok");
	}

	/** Ready when the database answers and both credential lists are loaded. */
	@GetMapping("/readyz")
	ResponseEntity<Map<String, Object>> readyz() {
		boolean db = dbUp();
		boolean keys = apiKeys.ready();
		boolean jwks = jwt.ready();
		boolean ready = db && keys && jwks;
		return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
				.body(Map.of("status", ready ? "ok" : "unavailable", "database", db, "api_keys", keys, "jwks", jwks));
	}

	private boolean dbUp() {
		try {
			return jdbc.sql("select 1").query(Integer.class).single() == 1;
		} catch (RuntimeException e) {
			return false;
		}
	}
}
