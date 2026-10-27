package protrader.funds.support;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real server on a random port, real Postgres (the primary key and "on conflict" are the point; a fake DB
 * would prove nothing). Postgres comes from Testcontainers, or from FUNDS_TEST_DB_URL (+ _USER, _PASSWORD)
 * if that is set. Credentials come from temp files written by TestCredentials.
 * The transfers table is emptied before every test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

	static final String EXTERNAL_DB = System.getenv("FUNDS_TEST_DB_URL");
	static final PostgreSQLContainer POSTGRES = EXTERNAL_DB == null ? new PostgreSQLContainer("postgres:17-alpine") : null;
	static final TestCredentials CREDENTIALS = TestCredentials.writeToTempDir();

	static {
		if (POSTGRES != null) {
			POSTGRES.start();
		}
		// application.properties reads these; one fixed set for the shared Spring context
		System.setProperty("INTERNAL_PORT", Integer.toString(freePort()));
		System.setProperty("JWKS_URL", CREDENTIALS.jwksFile.toString());
		System.setProperty("API_KEYS_SOURCE", CREDENTIALS.apiKeysFile.toString());
	}

	private static int freePort() {
		try (ServerSocket s = new ServerSocket(0)) {
			return s.getLocalPort();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		if (POSTGRES != null) {
			registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
			registry.add("spring.datasource.username", POSTGRES::getUsername);
			registry.add("spring.datasource.password", POSTGRES::getPassword);
		} else {
			registry.add("spring.datasource.url", () -> EXTERNAL_DB);
			registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("FUNDS_TEST_DB_USER", "postgres"));
			registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("FUNDS_TEST_DB_PASSWORD", "postgres"));
		}
	}

	@LocalServerPort
	protected int port;

	@Value("${funds.internal-port}")
	protected int internalPort;

	@Autowired
	protected JdbcClient jdbc;

	@BeforeEach
	protected void emptyTables() {
		jdbc.sql("truncate transfers").update();
	}

	/** Public port, logged in as this account with a JWT. */
	protected Http as(long accountId) {
		return new Http("http://127.0.0.1:" + port, Map.of("Authorization", "Bearer " + TestCredentials.token(accountId)));
	}

	/** Public port with exactly these headers (for auth tests). */
	protected Http withHeaders(Map<String, String> headers) {
		return new Http("http://127.0.0.1:" + port, headers);
	}

	/** Internal port, no credentials. */
	protected Http internal() {
		return new Http("http://127.0.0.1:" + internalPort, Map.of());
	}

	protected long rowCount() {
		return jdbc.sql("select count(*) from transfers").query(Long.class).single();
	}
}
