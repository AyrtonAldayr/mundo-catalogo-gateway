# Kubernetes

La estructura de rutas es la misma que en local; solo cambia el valor de `uri` por el nombre del Service (DNS interno del cluster).

- **Mismo namespace**: `uri: http://catalog-api:8080`
- **Otro namespace**: `uri: http://catalog-api.<namespace>.svc.cluster.local:8080`

**Config Server** en K8s: `spring.config.import=optional:configserver:http://config-server:8888` (donde `config-server` es el nombre del Service del Config Server).

**Secrets / issuer JWT**: usar ConfigMaps, Secrets o Config Server para las URIs y contraseñas; no dejar valores sensibles en el YAML del repositorio.

---

## Construir la imagen Docker

Desde la raíz del proyecto:

```bash
docker build -t mundo-catalogo-gateway:latest .
```

Opcionalmente, generar el JAR antes: `mvn package -DskipTests`, luego `docker build -t mundo-catalogo-gateway:latest .`.

---

## Ejecutar con Docker Compose

Desarrollo (construye y levanta el gateway):

```bash
docker compose up -d
```

Producción (imagen ya construida, con límites de recursos):

```bash
docker compose -f compose.yaml -f compose.prod.yaml up -d
```

---

## Desplegar en Kubernetes

1. Construir y, si aplica, subir la imagen al registro que use el cluster (o usar la imagen local con `imagePullPolicy: IfNotPresent` en un cluster que tenga la imagen).

2. Aplicar los manifiestos (ConfigMap, Deployment, Service):

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

O en un solo paso:

```bash
kubectl apply -f k8s/
```

(Excluir `k8s/secret.yaml.example`; usar un Secret real creado a partir del ejemplo y no commitearlo.)

3. Para JWT u otros secretos: copiar `k8s/secret.yaml.example` a `k8s/secret.yaml`, rellenar los valores en base64, aplicar y referenciar el Secret en el Deployment (por ejemplo con `env.valueFrom.secretKeyRef`).

---

## Decisiones de diseño y optimización

Ver [docker-kubernetes-investigacion.md](docker-kubernetes-investigacion.md) para layering, recursos JVM (`MaxRAMPercentage`), probes, seguridad y buenas prácticas.
