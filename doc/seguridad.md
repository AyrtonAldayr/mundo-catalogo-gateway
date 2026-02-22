# Seguridad

## Alcance: gateway vs microservicios

| Capa | Responsabilidad | Dónde se hace |
|------|-----------------|----------------|
| **Gateway (este MS)** | **Autenticación**: comprobar que la petición lleva un token JWT válido (firma, expiración, issuer) en los paths configurados. No decide permisos sobre recursos concretos. | `gateway.security.secured-path-patterns` + Spring Security OAuth2 Resource Server |
| **Cada microservicio (backend)** | **Autorización**: decidir si el usuario/rol puede hacer esta acción sobre este recurso (ej. “¿puede editar este documento?”, “¿ver datos de este tenant?”). Usa el token o claims que el gateway puede reenviar en headers. | Lógica propia de cada MS (roles, ownership, políticas) |

El gateway solo responde “¿quién es?” y “¿tiene token válido?”. Quién puede hacer qué con qué recurso lo resuelve cada MS.

---

## Rotación de llaves y caché del JWK Set

El gateway obtiene las claves públicas (JWK Set) del IdP y las cachea en memoria. Cuando el IdP (p. ej. Keycloak) rota llaves, el gateway usa las nuevas tras expirar ese caché. En `application.yaml` hay comentarios sobre el tiempo de vida del caché; si la versión de Spring Boot lo expone, se puede ajustar con la propiedad correspondiente; si no, puede hacerse con un bean `ReactiveJwtDecoder` propio que configure el caché.

---

## Proteger el acceso al IdP (Keycloak): mTLS

Por defecto solo se valida el certificado del servidor (HTTPS) al llamar al endpoint de JWKs. Si quieres que solo el gateway (u otros servicios autorizados) puedan acceder al IdP, opciones típicas son: restricción por red (firewall / políticas) o **mTLS** (certificado de cliente). Para mTLS hacia Keycloak (o el proxy delante), la auto-configuración del resource server no configura certificado de cliente; hay que definir un **`ReactiveJwtDecoder`** propio que use un `WebClient` con el certificado de cliente (ver más abajo: SSL Bundles).

### Cómo generar certificados para mTLS (OpenSSL)

Resumen mínimo: crear una CA, firmar el certificado del servidor (Keycloak o proxy) y el del cliente (gateway). Luego el cliente usa su cert + clave para autenticarse.

```bash
# 1. CA: clave y certificado autofirmado
openssl genrsa -out ca.key 2048
openssl req -new -x509 -days 365 -key ca.key -out ca.crt -subj "/CN=MiCA"

# 2. Certificado del cliente (gateway): clave, CSR, firmar con la CA
openssl genrsa -out client.key 2048
openssl req -new -key client.key -out client.csr -subj "/CN=gateway"
openssl x509 -req -days 365 -in client.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out client.crt

# 3. Empaquetar cliente en PKCS12 (para Spring: keystore)
openssl pkcs12 -export -inkey client.key -in client.crt -out client.p12 -name gateway -passout pass:changeit
```

El servidor (Keycloak o proxy) debe estar configurado para exigir cliente con cert firmado por esa CA (y tener el certificado del servidor firmado por la misma CA o que el cliente confíe en él). En Kubernetes se suelen usar herramientas como cert-manager o secretos con los certs.

### Simplificar la configuración: SSL Bundles (Spring Boot 3.1+)

Spring Boot **SSL Bundles** evita cargar certificados manualmente en código. Defines un bundle en YAML (keystore del cliente + truststore con la CA o el cert del servidor) y lo aplicas a un `WebClient` con **`WebClientSsl`**. Así no montas tú el `SSLContext` a mano.

Ejemplo de configuración (el gateway usa el bundle `idp-client` para llamar al IdP):

```yaml
spring:
  ssl:
    bundle:
      jks:
        idp-client:
          key:
            alias: gateway
          keystore:
            location: file:./config/client.p12
            password: ${KEYSTORE_PASSWORD}
            type: PKCS12
          truststore:
            location: file:./config/ca-trust.p12
            password: ${TRUSTSTORE_PASSWORD}
```

En código solo hace falta un bean que construya un `WebClient` con `webClientBuilder.apply(ssl.fromBundle("idp-client"))` y un **`ReactiveJwtDecoder`** que use ese `WebClient` para resolver el JWK Set (p. ej. `NimbusReactiveJwtDecoder` con un `ReactiveJwkSetUriHandler` que use ese cliente). Spring no conecta por propiedad el resource server con un SSL bundle; ese bean custom es el “puente”. Documentación: [Spring Boot SSL](https://docs.spring.io/spring-boot/reference/features/ssl.html), [WebClientSsl](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/web/reactive/function/client/WebClientSsl.html).
