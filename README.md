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

## Estructura del código

- `config/` — Propiedades (headers, seguridad JWT, paths protegidos)
- `filter/` — Filtro global de headers obligatorios
- `i18n/` — Mensajes por idioma
- Configuración: `src/main/resources/application.yaml` y `application-<perfil>.yml`

## Licencia

Uso privado. Ver [LICENSE](LICENSE).
