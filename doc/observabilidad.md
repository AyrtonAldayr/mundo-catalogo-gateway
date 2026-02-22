# Observabilidad y logs

El gateway exporta **métricas**, **trazas** y **logs** mediante **OpenTelemetry** y el protocolo **OTLP**. Un backend (OpenTelemetry Collector, stack LGTM de Grafana, etc.) puede recibir estas señales y exponerlas en **Grafana** para monitoreo y trazabilidad de peticiones.

## Resumen: qué expone el gateway

| Señal    | Contenido típico                          | Consumido por Grafana como |
|----------|-------------------------------------------|-----------------------------|
| **Métricas** | Contadores y gauges (HTTP, JVM, etc.)     | Origen de datos tipo Prometheus/Mimir (vía OTLP) |
| **Trazas**   | Spans por petición (gateway → backends)   | Origen Tempo/Jaeger (vía OTLP) |
| **Logs**     | Líneas de log con contexto (trace-id)    | Origen Loki (vía OTLP) |

El gateway **no** expone un endpoint HTTP para que Grafana “rasquee” directamente. **Envía** los datos a una URL OTLP (HTTP o gRPC) que tú configuras. Quien recibe es un **Collector** o un backend con ingest OTLP (Loki, Tempo, Mimir, etc.); Grafana se conecta a esos backends como data sources.

## Endpoints OTLP que el gateway usa (salida)

El gateway actúa como **cliente OTLP**: envía a las URLs que configuras. Por defecto (perfil `local`) apunta a `localhost:4318`. En producción debes apuntar al Collector o al backend de tu entorno.

| Propiedad | Valor por defecto | Descripción |
|-----------|--------------------|-------------|
| `management.otlp.metrics.export.url` | `http://localhost:4318/v1/metrics` | Envío de métricas (Micrometer → OTLP). |
| `management.opentelemetry.tracing.export.otlp.endpoint` | `http://localhost:4318/v1/traces` | Envío de trazas. |
| `management.opentelemetry.logging.export.otlp.endpoint` | `http://localhost:4318/v1/logs` | Envío de logs (appender Logback → OTLP). |

- **Puerto 4318**: OTLP sobre HTTP.  
- **Puerto 4317**: OTLP sobre gRPC (si tu backend lo usa, cambia la URL o el path según documentación del backend).

Sobrescritura por perfil: en `application-produccion.yml` (o Config Server) se pueden definir URLs distintas, por ejemplo `http://otel-collector:4318/v1/metrics`, etc.

## Logging a archivo (local)

Además de enviar logs por OTLP, el gateway escribe logs en **archivo** en el servidor.

| Propiedad / Ubicación | Valor / Detalle |
|------------------------|------------------|
| Ruta del archivo | `logging.file.name` en `application.yaml` (p. ej. `./logs/mundo-catalogo-gateway.log`). |
| Rotación y retención | Definidas en `src/main/resources/logback-spring.xml`: tamaño máximo por archivo **10MB**, **30** días de historial, **500MB** máximo total. |
| Patrón de archivos rotados | `mundo-catalogo-gateway.log-yyyy-MM-dd.i.log`. |

La carpeta `logs/` y los `*.log` están en **.gitignore** (no se versionan).

## Cómo se produce cada señal

1. **Métricas**: Spring Boot y el gateway generan métricas con Micrometer; el starter OpenTelemetry exporta esas métricas por OTLP a `management.otlp.metrics.export.url`.
2. **Trazas**: Las peticiones HTTP al gateway generan spans (Micrometer Tracing + bridge OTel). Se exportan a `management.opentelemetry.tracing.export.otlp.endpoint`. La propagación de contexto (trace-id) a los backends se hace vía headers estándar (W3C Trace Context).
3. **Logs**: Logback escribe en consola, en archivo (ROLLING) y en el **appender OTel**. El appender OTel envía cada evento de log al SDK de OpenTelemetry, que lo exporta a `management.opentelemetry.logging.export.otlp.endpoint`. Un bean (`InstallOpenTelemetryAppender`) registra el appender con el `OpenTelemetry` del contexto.

## Cabecera X-Trace-Id (trazabilidad)

El gateway añade la cabecera de respuesta **`X-Trace-Id`** con el identificador de la traza actual cuando existe. Así, ante un error, el cliente puede reportar ese valor y en Grafana (Tempo/Loki) buscar la traza y los logs asociados a esa petición.

## Qué necesita Grafana para consumir los datos

Grafana **no** se conecta directamente al gateway. Necesitas un **backend** que reciba OTLP y al que Grafana se conecte como data source:

1. **OpenTelemetry Collector**: recibe OTLP del gateway y reenvía a Prometheus/Mimir (métricas), Tempo/Jaeger (trazas), Loki (logs). Grafana usa Mimir, Tempo y Loki como data sources.
2. **Stack LGTM (Loki, Grafana, Tempo, Mimir)**: imagen `grafana/otel-lgtm` o despliegue equivalente que expone un endpoint OTLP y ya tiene Loki, Tempo y Mimir configurados; Grafana viene con data sources apuntando a ellos.
3. **Backends que aceptan OTLP nativo**: por ejemplo Loki 3.x con ingest OTLP, Tempo con OTLP, etc. El gateway envía a la URL OTLP de ese backend; en Grafana añades el data source correspondiente.

Resumen: el gateway envía a las URLs configuradas en `management.otlp.*` y `management.opentelemetry.*`. Quien recibe (Collector o backend) debe estar levantado y accesible; luego en Grafana configuras los data sources (Mimir/Prometheus para métricas, Tempo para trazas, Loki para logs) según ese backend.

## Actuator

Los endpoints de Actuator expuestos son `health` e `info` (`management.endpoints.web.exposure.include`). Sirven para comprobar que la aplicación está viva; no son el canal de observabilidad para Grafana (ese es OTLP).

## Referencia rápida de propiedades

| Propiedad | Dónde | Uso |
|-----------|--------|-----|
| `management.endpoints.web.exposure.include` | application.yaml | Endpoints actuator (health, info). |
| `management.otlp.metrics.export.url` | application.yaml / perfil | URL OTLP para métricas. |
| `management.opentelemetry.tracing.export.otlp.endpoint` | application.yaml / perfil | URL OTLP para trazas. |
| `management.opentelemetry.logging.export.otlp.endpoint` | application.yaml / perfil | URL OTLP para logs. |
| `logging.file.name` | application.yaml | Ruta del archivo de log. |
| `logging.logback.rollingpolicy.*` | application.yaml | Límites de rotación (detalle efectivo en logback-spring.xml). |

Configuración de Logback (appenders CONSOLE, ROLLING, OTEL): `src/main/resources/logback-spring.xml`.
