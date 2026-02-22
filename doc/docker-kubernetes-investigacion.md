# Investigación: Docker, Docker Compose y Kubernetes para mundo-catalogo-gateway

Documento de investigación sobre mejores prácticas (Spring, comunidad, Kubernetes) para contenedorizar y desplegar el gateway con **máxima optimización de recursos**. Sirve como base para definir el plan de implementación.

---

## 1. Contexto del proyecto

- **Stack**: Spring Boot 4.0.3, Java 25, Spring Cloud Gateway (WebFlux), Config Server (opcional), Actuator, OpenTelemetry, Security/OAuth2.
- **Perfiles**: `local`, `desarrollo`, `produccion`.
- **Objetivo**: Docker + Docker Compose + manifiestos/listos para Kubernetes, optimizando CPU/memoria y buenas prácticas.

---

## 2. Docker: mejores prácticas

### 2.1 Recomendación oficial de Spring Boot

Spring Boot documenta explícitamente el uso de **imágenes eficientes** mediante:

- **Layering del JAR**: no copiar el uber JAR tal cual. Usar el índice de capas (`layers.idx`) que separa:
  - `dependencies` (dependencias estables)
  - `spring-boot-loader`
  - `snapshot-dependencies`
  - `application` (código de la app)

- **Ventajas**:
  - Docker reutiliza capas en cache: solo se reconstruye lo que cambia (normalmente la capa `application`).
  - Menor tamaño efectivo en pull y mejor tiempo de arranque.
  - Compatible con **AOT cache** (Java 24+) y **CDS** (Class Data Sharing) para arranques más rápidos.

- **Herramienta**: `spring-boot-jarmode-tools` (incluido cuando el JAR tiene layering). Comando:
  ```bash
  java -Djarmode=tools -jar application.jar extract --layers --destination extracted
  ```

- **Dockerfile multi-stage (recomendado por Spring)**:
  1. **Stage builder**: imagen con JRE (ej. `bellsoft/liberica-openjre-debian:25-cds`), copiar JAR y ejecutar `extract`.
  2. **Stage runtime**: misma base ligera; copiar cada capa con `COPY --from=builder` en orden (dependencies → spring-boot-loader → snapshot-dependencies → application).
  3. `ENTRYPOINT ["java", "-jar", "application.jar"]` (el JAR en runtime es el “slim” que referencia las capas extraídas).

- **Opcional**: Si se quiere **AOT cache** (Java 24+), en el stage final se puede añadir un `RUN` de “training” y arrancar con `-XX:AOTCache=app.aot` para reducir aún más el tiempo de arranque.

**Conclusión**: Usar Dockerfile multi-stage con extracción de capas vía jarmode y, si aplica, AOT cache. Base oficial usada en la doc: Liberica JRE Debian 25 con soporte CDS.

### 2.2 Imagen base

- Preferir imágenes **oficiales o verificadas** (Docker Official, Verified Publisher) para reducir superficie de ataque y tamaño.
- **Eclipse Temurin**, **BellSoft Liberica**, **Amazon Corretto** son habituales para Java en producción.
- Evitar imágenes “full JDK” en runtime: usar **JRE** o **JRE slim** (cuando exista para Java 25).
- **Distroless** (Google): muy mínima y segura, pero para Java 25 hay que comprobar disponibilidad; si se usa, el JAR debe poder ejecutarse sin shell (ENTRYPOINT con `java -jar`).

### 2.3 Multi-stage y tamaño

- **Siempre** multi-stage: etapa de **build** (Maven + JDK) y etapa de **runtime** (solo JRE + artefactos).
- No llevar compilador, Maven ni código fuente a la imagen final.
- **.dockerignore** obligatorio: `target/` excepto el JAR final, `*.md`, `.git`, `.idea`, etc., para reducir contexto y tiempo de build.

### 2.4 Resumen Docker

| Práctica | Descripción |
|----------|-------------|
| Layering | JAR con capas + jarmode extract en builder, COPY por capas en runtime |
| Multi-stage | Build en una imagen, runtime en otra mínima |
| Base | JRE oficial/slim (Liberica/Temurin/Corretto) Java 25 |
| .dockerignore | Excluir todo lo que no sea necesario para el COPY del JAR |
| AOT cache (opcional) | Java 24+: training run en imagen y arranque con `-XX:AOTCache=...` |

---

## 3. JVM en contenedores (optimización de memoria)

### 3.1 Problema

La JVM usa memoria **fuera del heap** (metaspace, code cache, threads, buffers nativos). Si solo se fija `-Xmx`, el proceso puede superar ese valor y en Kubernetes el contenedor puede recibir **OOMKilled** cuando supere el `limit` de memoria.

### 3.2 Recomendación: MaxRAMPercentage

- Usar **`-XX:MaxRAMPercentage`** (p. ej. `75.0`) para que la JVM calcule el heap máximo como **porcentaje del límite de memoria del contenedor** (cgroup).
- La JVM moderna es “container-aware” y lee el límite del cgroup; así no hay que calcular `-Xmx` a mano ni usar scripts.
- **Valor típico**: 75–80%. El resto queda para metaspace, code cache, etc.
- Ejemplo: límite 1 GiB → con 75% → ~768 MiB heap máximo.

### 3.3 Dónde configurarlo

- En el **Dockerfile**: `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "application.jar"]`, o
- En **Kubernetes**: variable de entorno `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0` (así no hace falta tocar la imagen para ajustar).

### 3.4 CPU

- No fijar **CPU limits** demasiado bajos (p. ej. 0.5) si el arranque de Spring Boot es pesado; puede alargar mucho el startup.
- **CPU requests** sí son útiles para scheduling y calidad de servicio.

**Conclusión**: Memoria limit vía Kubernetes + `-XX:MaxRAMPercentage=75.0` (o similar); evitar solo `-Xmx` fijo. CPU limits con cuidado para no perjudicar el arranque.

---

## 4. Docker Compose

### 4.1 Uso típico

- **Desarrollo**: levantar gateway + dependencias (Config Server, etc.) con un `compose.yaml` (o `docker-compose.yaml`).
- **Producción**: Compose puede usarse para despliegues pequeños; para clusters reales lo habitual es Kubernetes.

### 4.2 Spring Boot y Compose

- Spring Boot 3.x/4.x puede integrarse con Docker Compose (lifecycle): levantar servicios definidos en `compose.yaml` al arrancar la app.
- Para **solo contenedorizar el gateway** (sin que Spring levante Compose), usamos Compose como orquestador externo: `docker compose up` construye/levanta el servicio del gateway y, si aplica, Config Server, etc.

### 4.3 Buenas prácticas Compose

- **Red**: red definida por usuario para aislar servicios.
- **Volúmenes**: solo donde haga falta persistencia (p. ej. datos de Config Server); no montar código en producción.
- **Variables de entorno**: no secretos en claro; usar `env_file` o secrets de Docker.
- **Recursos** (producción): `deploy.resources.limits` y `reservations` (CPU/memoria) para no consumir todo el host.
- **Restart**: `restart: unless-stopped` o `always` en producción.
- **Healthcheck**: definir `healthcheck` en el servicio del gateway (por ejemplo contra `/actuator/health`) para que Compose sepa cuándo está listo.

### 4.4 Desarrollo vs producción

- **Desarrollo**: puede usar `build: .` y volúmenes de código si se desea; perfil `local`.
- **Producción**: imagen ya construida (`image: ...`), sin bind mounts de código, con límites de recursos y políticas de restart.

**Conclusión**: Un `compose.yaml` base (gateway + opcionalmente Config Server), con healthcheck y recursos; opcionalmente `compose.override.yaml` para desarrollo y `compose.prod.yaml` para producción.

---

## 5. Kubernetes: mejores prácticas

### 5.1 Recursos (requests y limits)

- **Memory limit** (obligatorio): evita que un pod consuma toda la memoria del nodo; si se supera, OOMKilled. Debe ser coherente con `-XX:MaxRAMPercentage` (p. ej. 512Mi–1Gi para un gateway ligero).
- **Memory request**: para scheduling; puede ser igual o algo menor que el limit.
- **CPU request**: recomendable para scheduling (ej. 100m–250m).
- **CPU limit**: opcional; si se pone muy bajo, el arranque puede ser lento; en muchos entornos se omite o se pone generoso para apps Java.

Ejemplo orientativo (ajustar según carga):

```yaml
resources:
  requests:
    memory: "384Mi"
    cpu: "200m"
  limits:
    memory: "768Mi"
    # cpu: opcional o 500m-1000m según cluster
```

### 5.2 Probes (salud)

- **Startup probe**: para aplicaciones con arranque lento (como Spring Boot). Mientras falla, no se ejecutan liveness/readiness. Evita que K8s reinicie el pod por “no listo” durante el startup.
- **Liveness probe**: comprueba si el proceso está vivo; si falla, Kubernetes reinicia el contenedor. Endpoint típico: `/actuator/health/liveness`.
- **Readiness probe**: comprueba si el pod puede recibir tráfico; si falla, se saca de los endpoints del Service. Endpoint: `/actuator/health/readiness`.

Spring Boot Actuator (con `management.endpoint.health.probes.enabled=true`) expone estos endpoints. En Kubernetes:

- **startupProbe**: `httpGet` a `/actuator/health/liveness` (o readiness), `failureThreshold: 30`, `periodSeconds: 5` (da hasta 150 s de arranque).
- **livenessProbe**: `httpGet` a `/actuator/health/liveness`, `periodSeconds: 10`, `failureThreshold: 3`.
- **readinessProbe**: `httpGet` a `/actuator/health/readiness`, `periodSeconds: 5`, `failureThreshold: 3`.

**Requisito**: En `application.yaml` hay que tener habilitados los probes (ya se vio que Actuator está presente; falta confirmar `management.health.livenessstate.enabled` y `readinessstate.enabled`).

### 5.3 Seguridad (securityContext)

- **runAsNonRoot: true**: que el contenedor no corra como UID 0.
- **readOnlyRootFilesystem: true**: recomendado en general, pero Spring Boot suele escribir en `/tmp`. Solución: montar un **emptyDir** en `/tmp` (o la ruta que use la app) con permisos de escritura para el usuario del contenedor.
- Usuario numérico (ej. `runAsUser: 1000`) en el pod o en la imagen (USER en Dockerfile).

**Conclusión**: runAsNonRoot + runAsUser; readOnlyRootFilesystem solo si se monta un volumen writable en `/tmp` (y se prueba que el gateway arranca).

### 5.4 Configuración y secrets

- **Config Server en K8s**: como ya se indica en `doc/kubernetes.md`, usar el nombre del Service (ej. `http://config-server:8888`) y `spring.config.import=optional:configserver:...`.
- **Secrets/ConfigMaps**: URIs de JWT, contraseñas, etc., vía variables de entorno inyectadas desde Secret/ConfigMap; no valores sensibles en el YAML del repo.

### 5.5 Otros aspectos útiles

- **Replicas**: Deployment con `replicas: 1` (o más) según disponibilidad y carga.
- **Service**: ClusterIP o LoadBalancer/NodePort según exposición.
- **HPA (Horizontal Pod Autoscaler)**: opcional; escalar por CPU o memoria cuando se superen umbrales (útil para optimizar uso de recursos del cluster).
- **PodDisruptionBudget**: opcional; para evitar que se bajen todos los pods a la vez en actualizaciones o mantenimiento.

**Conclusión**: Deployment con recursos, probes, securityContext, Service y configuración vía ConfigMap/Secret; opcional HPA y PDB.

---

## 6. Resumen: optimización de recursos

| Área | Acción |
|------|--------|
| **Imagen** | Layering + multi-stage + base JRE slim → menos tamaño, menos transferencia, cache eficiente. |
| **JVM** | `-XX:MaxRAMPercentage=75.0` y memory limit en K8s → sin OOM, uso predecible. |
| **CPU** | Requests para scheduling; limits no demasiado bajos para no alargar arranque. |
| **K8s** | requests/limits coherentes; startup/liveness/readiness para no reinicios innecesarios ni tráfico a pods no listos. |
| **Compose** | Límites y reservas en `deploy.resources`; healthcheck para dependencias. |
| **Seguridad** | runAsNonRoot, runAsUser; readOnlyRootFilesystem con /tmp writable si se usa. |

---

## 7. Pendientes en el proyecto (para el plan)

- **pom.xml**: asegurar que el **spring-boot-maven-plugin** genere JAR con **layers** (layered jar) para que `jarmode=tools` y `extract` funcionen.
- **application.yaml**: habilitar **liveness/readiness** en Actuator (`management.health.livenessstate.enabled`, `readinessstate.enabled`, `management.endpoint.health.probes.enabled`) si no están ya.
- **Logback en contenedor**: en entorno contenedorizado, evitar escritura a `./logs/` si no se monta un volumen (o usar solo stdout); el log del log actual puede chocar con filesystem read-only o con múltiples pods escribiendo al mismo path.

---

## 8. Referencias utilizadas

- Spring Boot: [Container Images](https://docs.spring.io/spring-boot/reference/packaging/container-images/index.html), [Dockerfiles](https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html), [Efficient Container Images](https://docs.spring.io/spring-boot/reference/packaging/container-images/efficient-images.html).
- Docker: [Best practices](https://docs.docker.com/build/building/best-practices), [Multi-stage builds](https://docs.docker.com/get-started/docker-concepts/building-images/multi-stage-builds).
- Kubernetes: [Manage resources](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers), [Liveness/Readiness/Startup Probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes), [Security Context](https://kubernetes.io/docs/tasks/configure-pod-container/security-context).
- Comunidad: JVM en K8s (MaxRAMPercentage, OOM), Spring Boot Actuator health probes, readOnlyRootFilesystem con Java/Spring (emptyDir en /tmp), Docker Compose production.

---

Cuando revises este documento, podemos armar el **plan de implementación** (orden de tareas, archivos a crear/modificar y ejemplos concretos para este repo).
