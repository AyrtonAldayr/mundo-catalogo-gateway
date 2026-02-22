# mundo-catalogo-gateway

API Gateway reactivo para **Mundo Catálogo**. Enruta y agrega las llamadas a los servicios de catálogo usando Spring Cloud Gateway, con configuración centralizada (Spring Cloud Config) y validación de token JWT en paths configurables.

## Tecnología

- **Java 25**, **Spring Boot 4**, **Spring Cloud Gateway** (WebFlux)
- **Spring Cloud Config** (opcional), **Spring Security** + **OAuth2 Resource Server**, mensajes i18n (`Accept-Language`)

## Qué hace

- **Enrutamiento**: por path (ej. `/api/**`) hacia el backend correspondiente (uri por perfil).
- **Headers obligatorios**: X-Request-Id, X-Client-Id, X-Tenant-Id, Accept-Language; validación por regex; 400 si falta o no cumple.
- **Autenticación (JWT)**: en los paths de `gateway.security.secured-path-patterns` exige token válido; 401 si no hay o es inválido.
- **i18n**: mensajes de error según `Accept-Language` (`src/main/resources/messages.yml`).
- **Observabilidad**: OpenTelemetry (métricas, trazas y logs) exportados por OTLP; cabecera de respuesta `X-Trace-Id` para correlacionar peticiones.

La configuración (rutas, paths protegidos, JWT) es por YAML o Config Server.

## Documentación

Detalle en la carpeta **[doc/](doc/)**:

- [doc/seguridad.md](doc/seguridad.md) — Alcance gateway vs microservicios, JWT, rotación de llaves, mTLS, certificados (OpenSSL), SSL Bundles
- [doc/configuracion.md](doc/configuracion.md) — Perfiles y propiedades principales
- [doc/kubernetes.md](doc/kubernetes.md) — Despliegue en Kubernetes

## Cómo ejecutar

```bash
./mvnw spring-boot:run
# Con perfil: --spring.profiles.active=local-config
```

Puerto por defecto: `8080`. Rutas de ejemplo en `application-local.yml`; en Kubernetes se usan nombres de Service (ver [doc/kubernetes.md](doc/kubernetes.md)).

## Observabilidad y logging

- **OpenTelemetry**: el gateway exporta métricas, trazas y logs por OTLP. Endpoints configurables en `management.otlp.metrics.export.url`, `management.opentelemetry.tracing.export.otlp.endpoint` y `management.opentelemetry.logging.export.otlp.endpoint` (por defecto `http://localhost:4318/v1/...`). En producción sobrescribir en `application-produccion.yml` o Config Server con la URL del Collector o backend (Loki, Tempo, Mimir, etc.).
- **Logs en archivo**: ruta, rotación y retención en `logging.file.name` y `logging.logback.rollingpolicy.*` en `application.yaml`; detalle en `src/main/resources/logback-spring.xml` (RollingFile + appender OTel).
- **Trazabilidad**: la cabecera de respuesta `X-Trace-Id` permite buscar la traza y logs asociados en Grafana (u otro backend OTLP).

Para visualizar en Grafana hace falta un backend OTLP (OpenTelemetry Collector, stack LGTM, etc.) desplegado por separado.

## Estructura del código

- `config/` — Propiedades (headers, seguridad JWT, paths protegidos), instalación del appender OTel
- `filter/` — Filtros globales (headers obligatorios, X-Trace-Id en respuesta)
- `i18n/` — Mensajes por idioma
- Configuración: `src/main/resources/application.yaml`, `application-<perfil>.yml`, `logback-spring.xml`

## Licencia

Uso privado. Ver [LICENSE](LICENSE).
