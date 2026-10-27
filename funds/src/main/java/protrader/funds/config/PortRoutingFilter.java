package protrader.funds.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import protrader.funds.utils.ErrorWriter;

/**
 * Keeps the two ports apart (same as the auth service): /internal/** answers only on the internal port
 * (callers there send no user credentials, so the port is the protection), and public endpoints answer
 * only on the public port. Health answers on both.
 */
public final class PortRoutingFilter extends OncePerRequestFilter {

	private final int internalPort;
	private final ErrorWriter errors;

	public PortRoutingFilter(int internalPort, ErrorWriter errors) {
		this.internalPort = internalPort;
		this.errors = errors;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String path = request.getRequestURI();
		boolean internalPath = path.equals("/internal") || path.startsWith("/internal/");
		boolean health = path.equals("/healthz") || path.equals("/readyz");
		boolean onInternalPort = internalPort > 0 && request.getLocalPort() == internalPort;

		boolean allowed = health || (onInternalPort ? internalPath : !internalPath);
		if (!allowed) {
			errors.write(response, HttpStatus.NOT_FOUND, "not_found", "no such endpoint");
			return;
		}
		chain.doFilter(request, response);
	}
}
