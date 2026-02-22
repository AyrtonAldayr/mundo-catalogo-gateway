package com.mundo.catalogo.gateway.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Registra el appender de Logback que envía logs al API de OpenTelemetry para su
 * exportación por OTLP. Requiere que {@code management.opentelemetry.logging.export.otlp.endpoint}
 * esté configurado y que logback-spring.xml defina el appender OTEL.
 */
@Component
public class InstallOpenTelemetryAppender implements InitializingBean {

	private final OpenTelemetry openTelemetry;

	public InstallOpenTelemetryAppender(OpenTelemetry openTelemetry) {
		this.openTelemetry = openTelemetry;
	}

	@Override
	public void afterPropertiesSet() {
		OpenTelemetryAppender.install(openTelemetry);
	}
}
