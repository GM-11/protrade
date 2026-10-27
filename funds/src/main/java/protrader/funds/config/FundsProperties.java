package protrader.funds.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under funds.* in application.properties. */
@ConfigurationProperties(prefix = "funds")
public record FundsProperties(
		int internalPort,
		String internalBindAddress,
		long maxTransferMinor,
		Auth auth) {

	public record Auth(
			String jwksUrl,
			String jwtIssuer,
			String jwtAudience,
			Duration jwksRefresh,
			String apiKeysSource,
			Duration apiKeysPoll) {
	}
}
