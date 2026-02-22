package com.mundo.catalogo.gateway.i18n;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;

/**
 * Carga los mensajes desde messages.yml y los resuelve por clave y idioma (Accept-Language).
 * Permite placeholders {0}, {1}, etc. en las cadenas.
 */
@Component
public class GatewayMessageSource {

    private static final String MESSAGES_RESOURCE = "messages.yml";
	private static final String DEFAULT_LOCALE = "en";

	/** locale -> (dottedKey -> template) */
	private Map<String, Map<String, String>> messagesByLocale = Map.of();

	@PostConstruct
	void loadMessages() {
		try {
			Yaml yaml = new Yaml();
			Map<String, Object> raw = yaml.load(new ClassPathResource(MESSAGES_RESOURCE).getInputStream());
			if (raw == null) {
				return;
			}
			Map<String, Map<String, String>> out = new LinkedHashMap<>();
			for (Map.Entry<String, Object> e : raw.entrySet()) {
				if (e.getValue() instanceof Map<?, ?> nested) {
					@SuppressWarnings("unchecked")
					Map<String, Object> nestedMap = (Map<String, Object>) nested;
					out.put(e.getKey(), flatten(nestedMap, ""));
				}
			}
			messagesByLocale = Map.copyOf(out);
		} catch (Exception ex) {
			throw new IllegalStateException("Could not load " + MESSAGES_RESOURCE, ex);
		}
	}

	private static Map<String, String> flatten(Map<String, Object> map, String prefix) {
		Map<String, String> out = new LinkedHashMap<>();
		for (Map.Entry<String, Object> e : map.entrySet()) {
			String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
			if (e.getValue() instanceof Map<?, ?> nested) {
				@SuppressWarnings("unchecked")
				Map<String, Object> nestedMap = (Map<String, Object>) nested;
				out.putAll(flatten(nestedMap, key));
			} else if (e.getValue() != null) {
				out.put(key, e.getValue().toString());
			}
		}
		return out;
	}

	/**
	 * Resuelve el idioma a partir del header Accept-Language (ej. "es-ES,en;q=0.9" -> "es").
	 */
	public String resolveLocale(String acceptLanguageHeader) {
		if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
			return DEFAULT_LOCALE;
		}
		String first = acceptLanguageHeader.split(",")[0].trim().split(";")[0].trim();
		if (first.length() >= 2) {
			String lang = first.substring(0, 2).toLowerCase();
			if (messagesByLocale.containsKey(lang)) {
				return lang;
			}
			if (first.length() >= 5 && first.charAt(2) == '-') {
				return lang;
			}
		}
		return DEFAULT_LOCALE;
	}

	/**
	 * Obtiene el mensaje para la clave y el idioma, sustituyendo {0}, {1}, ... por los argumentos.
	 */
	public String getMessage(String key, String locale, Object... args) {
		String template = Optional.ofNullable(messagesByLocale.get(locale))
			.map(m -> m.get(key))
			.or(() -> Optional.ofNullable(messagesByLocale.get(DEFAULT_LOCALE)).map(m -> m.get(key)))
			.orElse(key);

		if (args == null || args.length == 0) {
			return template;
		}
		String result = template;
		for (int i = 0; i < args.length; i++) {
			result = result.replace("{" + i + "}", args[i] != null ? args[i].toString() : "");
		}
		return result;
	}
}
