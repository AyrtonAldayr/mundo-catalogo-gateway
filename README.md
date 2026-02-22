# mundo-catalogo-gateway

API Gateway reactivo para **Mundo Catálogo**. Enruta y agrega las llamadas a los servicios de catálogo usando Spring Cloud Gateway, con configuración centralizada (Spring Cloud Config) y validación de token JWT en paths configurables.

## Tecnología

- **Java 25**, **Spring Boot 4**, **Spring Cloud Gateway** (WebFlux)
- **Spring Cloud Config** (opcional): rutas, paths protegidos y JWT configurables por entorno
- **Spring Security** + **OAuth2 Resource Server**: validación JWT solo en los paths que se indiquen en config
- Mensajes de error en varios idiomas (`Accept-Language`)

## Qué hace este proyecto

- **Enrutamiento**: recibe peticiones por path (ej. `/api/**`) y las reenvía al backend correspondiente (uri por perfil).
- **Headers obligatorios**: exige headers configurados (X-Request-Id, X-Client-Id, X-Tenant-Id, Accept-Language) y valida su formato con regex; si falta alguno o no cumple, responde 400.
- **Autenticación (JWT)**: en los paths definidos en `gateway.security.secured-path-patterns` exige `Authorization: Bearer <token>` y valida el JWT (issuer/firma). Si no hay token o es inválido, responde 401.
- **i18n**: mensajes de error del gateway según `Accept-Language` (ver `src/main/resources/messages.yml`).

Las rutas, los paths protegidos y la URI del Config Server / JWT se configuran por YAML o Config Server; no hace falta recompilar para cambiar entornos (local, Kubernetes, etc.).

## Alcance de la seguridad y responsabilidad por capa

| Capa | Responsabilidad | Dónde se hace |
|------|-----------------|----------------|
| **Gateway (este MS)** | **Autenticación**: comprobar que la petición lleva un token JWT válido (firma, expiración, issuer) en los paths configurados. No decide permisos sobre recursos concretos. | `gateway.security.secured-path-patterns` + Spring Security OAuth2 Resource Server |
| **Cada microservicio (backend)** | **Autorización**: decidir si el usuario/rol puede hacer esta acción sobre este recurso (ej. “¿puede editar este documento?”, “¿ver datos de este tenant?”). Usa el token o claims que el gateway puede reenviar en headers. | Lógica propia de cada MS (roles, ownership, políticas) |

El gateway solo responde “¿quién es?” y “¿tiene token válido?”. Quién puede hacer qué con qué recurso lo resuelve cada MS.

## Perfiles

| Perfil | Uso | Config Server | Seguridad JWT |
|--------|-----|----------------|---------------|
| `local` | Desarrollo en máquina sin IdP | No | Desactivada (`gateway.security.enabled: false`) |
| `local-config` | Local con Config Server en `localhost:8888` | Sí (opcional) | Configurable |
| `desarrollo` | Integración/QA | Sí (opcional) | Configurable |
| `produccion` | Producción | Sí (opcional) | Configurable |

Activar perfil: `--spring.profiles.active=local` (por defecto `local`).

Rutas y URIs de backends se definen en `application-<perfil>.yml` bajo `spring.cloud.gateway.server.webflux.routes`. Paths protegidos por JWT en `gateway.security.secured-path-patterns` (en `application.yaml` o Config Server).

## Configuración relevante

- **Rutas**: `spring.cloud.gateway.server.webflux.routes` (id, uri, predicates). Un path por id; si el mismo backend atiende varios paths, varias rutas con la misma `uri`.
- **Paths que exigen JWT**: `gateway.security.secured-path-patterns`. Formato: `path` (cualquier método) o `path:GET,POST`. No depende de si se corre en local, contenedores o Kubernetes.
- **JWT**: `spring.security.oauth2.resourceserver.jwt.issuer-uri` (o `jwk-set-uri`) apuntando al Authorization Server. Se puede sobrescribir por perfil o Config Server.
- **Headers obligatorios**: `gateway.required-headers` (lista de cabeceras y regex). Aplican a todo el gateway.

Detalle y ejemplos en `src/main/resources/application.yaml`.

### Rotación de llaves y caché del JWK Set

El gateway obtiene las claves públicas (JWK Set) del IdP y las cachea en memoria. Cuando el IdP (p. ej. Keycloak) rota llaves, el gateway usa las nuevas tras expirar ese caché. En `application.yaml` hay comentarios sobre el tiempo de vida del caché; si la versión de Spring Boot lo expone, se puede ajustar con la propiedad correspondiente; si no, puede hacerse con un bean `ReactiveJwtDecoder` propio que configure el caché.

### Proteger el acceso al IdP (Keycloak): mTLS

Por defecto solo se valida el certificado del servidor (HTTPS) al llamar al endpoint de JWKs. Si quieres que solo el gateway (u otros servicios autorizados) puedan acceder al IdP, opciones típicas son: restricción por red (firewall / políticas) o **mTLS** (certificado de cliente). Para mTLS hacia Keycloak (o el proxy delante), la auto-configuración del resource server no configura certificado de cliente; hay que definir un **`ReactiveJwtDecoder`** propio que use un `WebClient` (o cliente HTTP reactivo) con un `SSLContext` que incluya el certificado de cliente, y registrar ese bean para que Spring Security lo use en lugar del decoder por defecto.

## Cómo ejecutar

```bash
./mvnw spring-boot:run
# Con perfil: --spring.profiles.active=local-config
```

Puerto por defecto en local: `8080`. Las rutas de ejemplo en `application-local.yml` apuntan a backends en `localhost:8081`, etc.; en Kubernetes se usan nombres de Service (ej. `http://catalog-api:8080`).

## Kubernetes

La estructura de rutas es la misma; solo cambia el valor de `uri` por el nombre del Service (DNS interno):

- Mismo namespace: `uri: http://catalog-api:8080`
- Otro namespace: `uri: http://catalog-api.<namespace>.svc.cluster.local:8080`

Config Server en K8s: `spring.config.import=optional:configserver:http://config-server:8888`. Secrets/issuer JWT vía ConfigMaps, Secrets o Config Server.

## Estructura del código

- `config/`: propiedades de configuración (headers obligatorios, seguridad JWT, paths protegidos).
- `filter/`: filtro global de headers obligatorios.
- `i18n/`: resolución de mensajes según idioma.
- Configuración: `src/main/resources/application.yaml` y `application-<perfil>.yml`.

## Licencia

Uso privado. Ver [LICENSE](LICENSE).
