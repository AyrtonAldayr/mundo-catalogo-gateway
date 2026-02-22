package com.mundo.catalogo.gateway.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Headers que el front debe enviar como mínimo y el patrón regex que debe cumplir cada valor.
 * Todo se define en application.yaml: clave = nombre del header, valor = expresión regular.
 * Si falta alguno o el valor no cumple el patrón, el gateway responde 400.
 * Para rutas privadas, el token (Authorization) se valida aparte (filtro o Spring Security por path).
 */

@ConfigurationProperties(prefix = "gateway.required-headers")
public record RequiredHeadersProperties(
    boolean enabled,
	Map<String, String> headers
) {
    public RequiredHeadersProperties() {
		this(true, Map.of());
	}
}
