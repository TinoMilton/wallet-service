package com.recargapay.wallet.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a request id: the caller's, from the X-Request-Id header,
 * or one generated here. The id is returned in the same response header and put
 * in the MDC before the rest of the chain runs, so every line logged while
 * handling the request carries it. The MDC is cleared afterwards, always, on
 * the request thread itself; the same thread, virtual or not, handles the
 * request from here to the response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	/** The header a request id travels in, in both directions. */
	static final String REQUEST_ID_HEADER = "X-Request-Id";

	/** The MDC key every log line written while handling the request carries. */
	static final String REQUEST_ID = "requestId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = theRequestIdOf(request);
		MDC.put(REQUEST_ID, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.clear();
		}
	}

	private static String theRequestIdOf(HttpServletRequest request) {
		String incoming = request.getHeader(REQUEST_ID_HEADER);
		return (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;
	}
}
