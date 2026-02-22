# Kubernetes

La estructura de rutas es la misma que en local; solo cambia el valor de `uri` por el nombre del Service (DNS interno del cluster).

- **Mismo namespace**: `uri: http://catalog-api:8080`
- **Otro namespace**: `uri: http://catalog-api.<namespace>.svc.cluster.local:8080`

**Config Server** en K8s: `spring.config.import=optional:configserver:http://config-server:8888` (donde `config-server` es el nombre del Service del Config Server).

**Secrets / issuer JWT**: usar ConfigMaps, Secrets o Config Server para las URIs y contraseñas; no dejar valores sensibles en el YAML del repositorio.
