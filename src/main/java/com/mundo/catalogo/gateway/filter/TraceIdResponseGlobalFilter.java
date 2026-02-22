package com.mundo.catalogo.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import reactor.core.publisher.Mono;

/**
 * Añade la cabecera de respuesta {@code X-Trace-Id} con el trace ID actual de
 * Micrometer Tracing cuando existe, para permitir correlacionar peticiones con
 * trazas y logs en Grafana.
 */
@Component
public class TraceIdResponseGlobalFilter implements GlobalFilter, Ordered {

	private static final String X_TRACE_ID = "X-Trace-Id";

	private final Tracer tracer;

	public TraceIdResponseGlobalFilter(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		Span span = tracer.currentSpan();
		if (span != null) {
			String traceId = span.context().traceId();
			if (traceId != null && !traceId.isEmpty()) {
				exchange.getResponse().getHeaders().add(X_TRACE_ID, traceId);
			}
		}
		return chain.filter(exchange);
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}
}
