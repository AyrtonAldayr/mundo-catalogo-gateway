package com.mundo.catalogo.gateway.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

	private static final String PATH_METHODS_SEPARATOR = ":";

	@Bean
	public SecurityWebFilterChain securityWebFilterChain(
			ServerHttpSecurity http,
			GatewaySecurityProperties securityProperties) {
		boolean enabled = securityProperties.enabled();
		List<String> rawPatterns = securityProperties.securedPathPatterns() != null
				? securityProperties.securedPathPatterns()
				: List.of();

		if (!enabled || rawPatterns.isEmpty()) {
			return http
					.csrf(ServerHttpSecurity.CsrfSpec::disable)
					.authorizeExchange(auth -> auth.anyExchange().permitAll())
					.build();
		}

		List<String> pathOnly = new ArrayList<>();
		List<PathWithMethods> pathWithMethods = new ArrayList<>();
		for (String entry : rawPatterns) {
			if (entry == null || entry.isBlank()) continue;
			int sep = entry.indexOf(PATH_METHODS_SEPARATOR);
			if (sep <= 0) {
				pathOnly.add(entry.trim());
			} else {
				String path = entry.substring(0, sep).trim();
				String methodsStr = entry.substring(sep + 1).trim();
				if (!path.isEmpty() && !methodsStr.isEmpty()) {
					Set<HttpMethod> methods = Stream.of(methodsStr.split(","))
							.map(String::trim)
							.filter(s -> !s.isEmpty())
							.map(HttpMethod::valueOf)
							.collect(Collectors.toSet());
					if (!methods.isEmpty()) {
						pathWithMethods.add(new PathWithMethods(path, methods));
					} else {
						pathOnly.add(path);
					}
				} else {
					pathOnly.add(path);
				}
			}
		}

		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange(auth -> {
					// Path sin restricción de método → cualquier método
					if (!pathOnly.isEmpty()) {
						auth.pathMatchers(pathOnly.toArray(new String[0])).authenticated();
					}
					// Path con métodos concretos
					for (PathWithMethods pwm : pathWithMethods) {
						for (HttpMethod method : pwm.methods()) {
							auth.pathMatchers(method, pwm.path()).authenticated();
						}
					}
					auth.anyExchange().permitAll();
				})
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
				.build();
	}

	private record PathWithMethods(String path, Set<HttpMethod> methods) {}
}
