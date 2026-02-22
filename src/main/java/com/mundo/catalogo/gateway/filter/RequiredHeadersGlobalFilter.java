package com.mundo.catalogo.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.mundo.catalogo.gateway.config.RequiredHeadersProperties;
import com.mundo.catalogo.gateway.i18n.GatewayMessageSource;

import reactor.core.publisher.Mono;

/**
 * Filtro que exige al front enviar los headers configurados en YAML y que sus valores
 * cumplan el patrón regex definido allí (evita inyección en headers). Si falta alguno
 * o el formato es inválido, responde 400 con un JSON indicando faltantes e inválidos.
 */
@Component
public class RequiredHeadersGlobalFilter implements GlobalFilter, Ordered {

	private static final int ORDER = -100;

	private final RequiredHeadersProperties properties;
	private final Map<String, Pattern> compiledPatterns;
	private final GatewayMessageSource messageSource;

	public RequiredHeadersGlobalFilter(RequiredHeadersProperties properties, GatewayMessageSource messageSource) {
		this.properties = properties;
		this.compiledPatterns = buildCompiledPatterns(properties);
		this.messageSource = messageSource;
	}

	private static Map<String, Pattern> buildCompiledPatterns(RequiredHeadersProperties props) {
		Map<String, String> headers = props.headers() != null ? props.headers() : Map.of();
		Map<String, Pattern> out = new HashMap<>();
		for (Map.Entry<String, String> e : headers.entrySet()) {
			if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null && !e.getValue().isBlank()) {
				out.put(e.getKey(), Pattern.compile(e.getValue()));
			}
		}
		return Map.copyOf(out);
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		if (!properties.enabled() || compiledPatterns.isEmpty()) {
			return chain.filter(exchange);
		}

		List<String> missing = new ArrayList<>();
		List<String> invalid = new ArrayList<>();

		for (Map.Entry<String, Pattern> e : compiledPatterns.entrySet()) {
			String headerName = e.getKey();
			Pattern pattern = e.getValue();
			String value = exchange.getRequest().getHeaders().getFirst(headerName);
			if (value == null || value.isBlank()) {
				missing.add(headerName);
				continue;
			}
			value = value.trim();
			if (!pattern.matcher(value).matches()) {
				invalid.add(headerName);
			}
		}

		if (missing.isEmpty() && invalid.isEmpty()) {
			return chain.filter(exchange);
		}

		exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

		String acceptLanguage = exchange.getRequest().getHeaders().getFirst("Accept-Language");
		List<String> required = List.copyOf(compiledPatterns.keySet());

		return messageSource
				.resolveLocale(acceptLanguage)
				.flatMap(locale -> buildErrorJson(missing, invalid, required, locale))
				.map(json -> exchange.getResponse().bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8)))
				.flatMap(buffer -> exchange.getResponse().writeWith(Mono.just(buffer)));
	}

	private Mono<String> buildErrorJson(List<String> missing, List<String> invalid, List<String> required, String locale) {
		String messageKey;
		Object[] messageArgs;
		if (!missing.isEmpty() && !invalid.isEmpty()) {
			messageKey = "gateway.error.both";
			messageArgs = new Object[] { String.join(", ", missing), String.join(", ", invalid) };
		} else if (!missing.isEmpty()) {
			messageKey = "gateway.error.missing_only";
			messageArgs = new Object[] { String.join(", ", missing) };
		} else {
			messageKey = "gateway.error.invalid_only";
			messageArgs = new Object[] { String.join(", ", invalid) };
		}

		Mono<String> titleMono = messageSource.getMessage("gateway.error.title", locale);
		Mono<String> messageMono = messageSource.getMessage(messageKey, locale, messageArgs);

		return Mono.zip(titleMono, messageMono).map(tuple -> {
			String title = tuple.getT1();
			String message = tuple.getT2();
			String missingJson = toJsonArray(missing);
			String invalidJson = toJsonArray(invalid);
			return "{\"error\":\"" + escapeJson(title) + "\",\"missingHeaders\":" + missingJson
					+ ",\"invalidFormatHeaders\":" + invalidJson
					+ ",\"message\":\"" + escapeJson(message) + "\"}";
		});
	}

	private static String toJsonArray(List<String> list) {
		return list.stream()
				.map(h -> "\"" + escapeJson(h) + "\"")
				.collect(Collectors.joining(", ", "[", "]"));
	}

	private static String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	@Override
	public int getOrder() {
		return ORDER;
	}
}
