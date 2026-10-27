package protrader.funds.config;

import java.io.IOException;
import java.util.OptionalLong;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import protrader.funds.utils.ErrorWriter;

/**
 * Guards /v2/** with the gateway's rules (order_gateway/auth/chain.go):
 *  - X-Api-Key-Id / X-Api-Secret present → API key decides (it wins over a bearer token, even if wrong)
 *  - else Authorization: Bearer <JWT>
 *  - else 401
 * Keys not loaded yet → 503 auth_unavailable. On success the account id goes in the request attribute
 * ACCOUNT_ID, which controllers read with @RequestAttribute.
 */
public final class AuthFilter extends OncePerRequestFilter {

	public static final String ACCOUNT_ID = "protrader.accountId";

	private final ApiKeys apiKeys;
	private final JwtVerifier jwt;
	private final ErrorWriter errors;

	public AuthFilter(ApiKeys apiKeys, JwtVerifier jwt, ErrorWriter errors) {
		this.apiKeys = apiKeys;
		this.jwt = jwt;
		this.errors = errors;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !(path.equals("/v2") || path.startsWith("/v2/"));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String keyId = request.getHeader("X-Api-Key-Id");
		String secret = request.getHeader("X-Api-Secret");
		String authorization = request.getHeader("Authorization");

		OptionalLong account;
		if (keyId != null || secret != null) {
			if (!apiKeys.ready()) {
				errors.write(response, HttpStatus.SERVICE_UNAVAILABLE, "auth_unavailable", "api keys not loaded yet");
				return;
			}
			account = apiKeys.verify(keyId, secret);
		} else if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
			if (!jwt.ready()) {
				errors.write(response, HttpStatus.SERVICE_UNAVAILABLE, "auth_unavailable", "token keys not loaded yet");
				return;
			}
			account = jwt.verify(authorization.substring(7).trim());
		} else {
			account = OptionalLong.empty();
		}

		if (account.isEmpty()) {
			errors.write(response, HttpStatus.UNAUTHORIZED, "unauthorized", "missing or invalid credentials");
			return;
		}
		request.setAttribute(ACCOUNT_ID, account.getAsLong());
		chain.doFilter(request, response);
	}
}
