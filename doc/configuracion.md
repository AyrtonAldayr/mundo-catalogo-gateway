# Configuración

## Perfiles

| Perfil | Uso | Config Server | Seguridad JWT |
|--------|-----|----------------|---------------|
| `local` | Desarrollo en máquina sin IdP | No | Desactivada (`gateway.security.enabled: false`) |
| `local-config` | Local con Config Server en `localhost:8888` | Sí (opcional) | Configurable |
| `desarrollo` | Integración/QA | Sí (opcional) | Configurable |
| `produccion` | Producción | Sí (opcional) | Configurable |

Activar perfil: `--spring.profiles.active=local` (por defecto `local`).

## Propiedades principales

- **Rutas**: `spring.cloud.gateway.server.webflux.routes` (id, uri, predicates). Un path por id; si el mismo backend atiende varios paths, varias rutas con la misma `uri`. Se definen en `application-<perfil>.yml`.
- **Paths que exigen JWT**: `gateway.security.secured-path-patterns`. Formato: `path` (cualquier método) o `path:GET,POST`. No depende de si se corre en local, contenedores o Kubernetes.
- **JWT**: `spring.security.oauth2.resourceserver.jwt.issuer-uri` (o `jwk-set-uri`) apuntando al Authorization Server. Se puede sobrescribir por perfil o Config Server.
- **Headers obligatorios**: `gateway.required-headers` (lista de cabeceras y regex). Aplican a todo el gateway.

Detalle y ejemplos en `src/main/resources/application.yaml`.
