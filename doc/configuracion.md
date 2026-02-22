# Configuración del Gateway

Configuración común en `src/main/resources/application.yaml`. Perfiles en `application-<perfil>.yml` (local, desarrollo, produccion). Activar: `--spring.profiles.active=local` (por defecto `local`).

---

## Perfiles

| Perfil | Uso | Config Server | Log a archivo |
|--------|-----|----------------|----------------|
| `local` | Desarrollo en máquina sin IdP | No | Sí (`./logs/`) |
| `local-container` | Contenedor local (docker compose): usar con `local` | No | No (solo consola) |
| `desarrollo` | Integración/QA (contenedor) | Sí (opcional) | No |
| `produccion` | Producción (contenedor) | Sí (opcional) | No |

- **Contenedor local**: `SPRING_PROFILES_ACTIVE=local,local-container`.
- **Kubernetes**: suele usarse `desarrollo` o `produccion`; en ambos el logging va solo a consola (stdout).

Ejemplo de perfil activo (en `application.yaml` o por defecto):

```yaml
spring:
  profiles:
    active: local   # otros: desarrollo, produccion; con contenedor local: local,local-container
```

Por línea de comandos: `--spring.profiles.active=desarrollo`. Por variable de entorno: `SPRING_PROFILES_ACTIVE=produccion`.

---

## Config Client (Config Server)

- En Spring Boot 2.4+ es obligatorio declarar `spring.config.import` si se usa Config Server.
- En `application.yaml` se define `optional:configserver:` (sin URL) para cuando **no** se usa Config Server (ej. perfil `local`).
- Los perfiles que sí usan Config Server definen la URL en su `application-<perfil>.yml`.

Sin Config Server (ej. perfil `local`):

```yaml
spring:
  config:
    import: "optional:configserver:"
```

Con Config Server (en `application-desarrollo.yml` o `application-produccion.yml`):

```yaml
spring:
  config:
    import: optional:configserver:http://config-server:8888
# En local con Config Server en localhost:
# import: optional:configserver:http://localhost:8888
```

---

## JWT (rutas protegidas)

Cuando `gateway.security.secured-path-patterns` no está vacío, Spring Security exige un JWT válido en esos paths. Definir **una** de estas dos opciones bajo `spring.security.oauth2.resourceserver.jwt` (en `application-<perfil>.yml` o Config Server):

1. **issuer-uri**: URL del IdP (recomendado si expone OpenID Connect). Spring resuelve el JWK Set.
2. **jwk-set-uri**: URL directa del endpoint de claves públicas (certs).

Ejemplo:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://idp.example.com/realms/mi-realm
          # O bien:
          # jwk-set-uri: https://idp.example.com/realms/mi-realm/protocol/openid-connect/certs
          # Opcional: tiempo de vida del caché de llaves (segundos); tras expirar se vuelve a pedir al IdP.
          # jwk-set-cache-lifespan: 300
          # jwk-set-cache-refresh-time: 60
```

---

## Rutas (backends)

Se definen por perfil en `application-<perfil>.yml` bajo `spring.cloud.gateway.server.webflux.routes`.

- **Un path por `id`**. Si el mismo backend atiende varios paths (ej. `/api-admin` y `/api-public`), definir una ruta por path con la misma `uri`.
- **Headers obligatorios**: no van por ruta; se aplican a todo el gateway (`gateway.required-headers`).
- **Método HTTP**: por defecto solo se define `Path`; la ruta acepta cualquier método. Para restringir: añadir en `predicates` algo como `Method=GET,POST`.

### Predicates disponibles (sintaxis corta; se combinan con AND)

| Predicate | Descripción |
|-----------|-------------|
| `Path=/api/**` | Path de la petición (Ant-style) |
| `Method=GET,POST` | Método HTTP |
| `Header=X-Request-Id,\d+` | Header con nombre y regex del valor |
| `Host=**.somehost.org` | Host header (Ant-style) |
| `Query=green` | Query param presente (regex opcional) |
| `Cookie=chocolate,ch\.p` | Cookie con nombre y regex |
| `RemoteAddr=192.168.1.1/24` | IP del cliente (CIDR) |
| `After=2025-01-01T00:00:00Z` | Después de fecha/hora (ZonedDateTime) |
| `Before=2025-12-31T23:59:59Z` | Antes de fecha/hora |
| `Between=datetime1,datetime2` | Entre dos fechas |
| `Weight=group1,8` | Peso para canary |
| `XForwardedRemoteAddr=192.168.0.0/16` | IP según X-Forwarded-For |

### Ejemplo de estructura de rutas

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: catalog-api-admin
              uri: http://localhost:8081
              predicates:
                - Path=/api-admin/**
            - id: catalog-api-public
              uri: http://localhost:8081
              predicates:
                - Path=/api-public/**
            - id: search-api
              uri: http://localhost:8082
              predicates:
                - Path=/search/**
            - id: inventory-api
              uri: http://localhost:8083
              predicates:
                - Path=/inventory/**
            - id: admin-api
              uri: http://localhost:8084
              predicates:
                - Path=/admin/**
                - Method=GET,POST   # opcional: restringir métodos
```

### Kubernetes

Las rutas son las mismas (`id`, `uri`, `predicates`); solo cambia el valor de `uri`. Secrets/issuer JWT: ConfigMaps, Secrets o Config Server.

Mismo namespace:

```yaml
routes:
  - id: catalog-api
    uri: http://catalog-api:8080
    predicates:
      - Path=/api/**
```

Otro namespace:

```yaml
routes:
  - id: catalog-api
    uri: http://catalog-api.otro-namespace.svc.cluster.local:8080
    predicates:
      - Path=/api/**
```

Config Server en K8s (en el YAML del perfil o ConfigMap):

```yaml
spring:
  config:
    import: optional:configserver:http://config-server:8888
```

---

## Seguridad del gateway

- **Paths que exigen JWT**: `gateway.security.secured-path-patterns` (Ant-style). Formato: `"path"` (cualquier método) o `"path:GET,POST"`. No depende del entorno (local, contenedor o K8s).
- **Headers obligatorios**: `gateway.required-headers`. Clave = nombre del header, valor = regex. Se aplican a todo el gateway; evitan inyección limitando caracteres permitidos.

Ejemplo completo (paths protegidos + headers obligatorios):

```yaml
gateway:
  security:
    enabled: true
    # path = cualquier método; path:GET,POST = solo esos métodos
    secured-path-patterns:
      - /api/inventory/**:GET,POST,PUT,DELETE
      - /api/list/**:POST
      - /api/admin/**
  required-headers:
    enabled: true
    headers:
      X-Request-Id: "^[a-zA-Z0-9\\-]{1,64}$"
      X-Client-Id: "^[a-zA-Z0-9_\\-.]{1,64}$"
      X-Tenant-Id: "^[a-zA-Z0-9_\\-]{1,64}$"
      Accept-Language: "^[a-z]{2}(-[A-Z]{2})?$"
```

---

## Mensajes de error (i18n)

Mensajes en `src/main/resources/messages.yml`; idioma según cabecera `Accept-Language`.

Ejemplo de estructura:

```yaml
# messages.yml (claves por código de error o mensaje)
invalid.token: Token inválido o expirado
missing.header: Falta el header obligatorio {0}
invalid.header: El header {0} no cumple el formato requerido
```

```yaml
# messages_en.yml
invalid.token: Invalid or expired token
missing.header: Required header {0} is missing
invalid.header: Header {0} does not match required format
```

---

## Management (health, endpoints, OpenTelemetry)

Configuración bajo `management` en `application.yaml`: health (liveness/readiness para Kubernetes), endpoints HTTP expuestos y exportación OTLP (métricas, trazas, logs). En producción sobrescribir las URLs OTLP en `application-produccion.yml` o Config Server.

Ejemplo completo (igual que en `application.yaml`; en producción cambiar las URLs de OTLP al Collector):

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
  endpoints:
    web:
      exposure:
        include: health,info
  otlp:
    metrics:
      export:
        url: http://localhost:4318/v1/metrics
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/traces
    logging:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/logs
```

- **health.probes**: habilita los endpoints `/live` y `/ready` para probes de Kubernetes.
- **endpoints.web.exposure.include**: expone `health` e `info` por HTTP (ajustar si necesitas más).
- **otlp / opentelemetry**: URLs del exportador OTLP (métricas, trazas, logs); en producción apuntar al Collector o backend (Loki, Tempo, Mimir, etc.).

---

## Logging

Ruta del archivo, rotación y retención en `logback-spring.xml` (propiedades `LOG_PATH`, `LOG_FILE` y appender ROLLING). En perfiles de contenedor solo se usa consola (stdout).

Ejemplo de propiedades (o variables de entorno) que usa logback:

```yaml
# Por defecto en logback-spring.xml:
# LOG_PATH=./logs
# LOG_FILE=${LOG_PATH}/mundo-catalogo-gateway.log
# Rotación: SizeAndTimeBasedRollingPolicy (10MB, 30 días, 500MB total)
```
