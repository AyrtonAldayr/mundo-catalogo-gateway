package com.mundo.catalogo.gateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paths que requieren token JWT válido (Authorization: Bearer). Configurable por YAML/Config Server.
 * Si enabled es false, no se exige token en ningún path (útil para local sin IdP).
 */
@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(
	boolean enabled,
	List<String> securedPathPatterns
) {
	public GatewaySecurityProperties() {
		this(true, List.of());
	}
}
