package protrader.funds.config;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.TaskScheduler;

import protrader.funds.config.ApiKeys;
import protrader.funds.config.AuthFilter;
import protrader.funds.config.JwksKeys;
import protrader.funds.config.JwtVerifier;
import protrader.funds.config.PortRoutingFilter;
import protrader.funds.utils.ErrorWriter;

@Configuration
public class AppConfig {

	private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	JwksKeys jwksKeys(FundsProperties props, Clock clock) {
		return new JwksKeys(props.auth().jwksUrl(), clock);
	}

	@Bean
	JwtVerifier jwtVerifier(JwksKeys keys, FundsProperties props, Clock clock) {
		return new JwtVerifier(keys, props.auth().jwtIssuer(), props.auth().jwtAudience(), clock);
	}

	@Bean
	ApiKeys apiKeys(FundsProperties props) {
		return new ApiKeys(props.auth().apiKeysSource());
	}

	/** First load at startup (failures only logged: /readyz stays 503), then on a timer. */
	@Bean
	ApplicationRunner credentialRefresh(JwksKeys jwks, ApiKeys apiKeys, FundsProperties props, TaskScheduler scheduler) {
		return args -> {
			boolean jwksLoaded = jwks.refresh();
			boolean keysLoaded = apiKeys.refresh();
			if (!jwksLoaded || !keysLoaded) {
				log.warn("credentials not loaded at startup; retrying on the timer (/readyz is 503 until then)");
			}
			scheduler.scheduleWithFixedDelay(jwks::refresh, props.auth().jwksRefresh());
			scheduler.scheduleWithFixedDelay(apiKeys::refresh, props.auth().apiKeysPoll());
			// until the first success, retry both quickly
			scheduler.scheduleWithFixedDelay(() -> {
				if (!jwks.ready()) {
					jwks.refresh();
				}
				if (!apiKeys.ready()) {
					apiKeys.refresh();
				}
			}, java.time.Duration.ofSeconds(2));
		};
	}

	// --- servlet filters, in this order: port routing, then auth

	@Bean
	FilterRegistrationBean<PortRoutingFilter> portRoutingFilter(FundsProperties props, ErrorWriter errors) {
		FilterRegistrationBean<PortRoutingFilter> bean = new FilterRegistrationBean<>(new PortRoutingFilter(props.internalPort(), errors));
		bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
		return bean;
	}

	@Bean
	FilterRegistrationBean<AuthFilter> authFilter(ApiKeys apiKeys, JwtVerifier jwt, ErrorWriter errors) {
		FilterRegistrationBean<AuthFilter> bean = new FilterRegistrationBean<>(new AuthFilter(apiKeys, jwt, errors));
		bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
		return bean;
	}

	/** Second listener for /internal/**. Keep this port off every public route. */
	@Bean
	WebServerFactoryCustomizer<TomcatServletWebServerFactory> internalConnector(FundsProperties props) {
		return factory -> {
			if (props.internalPort() > 0) {
				var connector = new org.apache.catalina.connector.Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
				connector.setPort(props.internalPort());
				connector.setProperty("address", props.internalBindAddress());
				factory.addAdditionalConnectors(connector);
			}
		};
	}
}
