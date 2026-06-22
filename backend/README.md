# Fil-Grama — Backend

Backend de la plataforma analítica de redes (Fil-Grama): autenticación JWT, onboarding OAuth de
cuentas sociales, captura diaria de métricas, y generación de reportes. **Spring Boot 4.0.0 · Java
(JDK) 25 · PostgreSQL**. La fuente de verdad del diseño está en `../spec/`.

## Requisitos

- **JDK 25** y **Maven 3.9+** (el proyecto fija `spring-boot-starter-parent` 4.0.0 y `java.version`
  25; no compila con JDK menor).
- **Docker + Docker Compose**: obligatorio para el Postgres de desarrollo y **para los tests e2e**
  (usan Testcontainers, que levanta un Postgres efímero real — sin Docker, esos tests fallan).

## Build y notas de infraestructura

`mvn clean package` compila, corre los 167+ tests (incluye e2e con Testcontainers) y arma el jar
`target/filgrama-backend-0.1.0-SNAPSHOT.jar`.

Tres ajustes de `pom.xml` fueron necesarios para que el build/run funcione en este stack (Boot 4 +
JDK 25) — si los tocás y se rompe, es por esto:

1. **`<maven.compiler.proc>full</maven.compiler.proc>`** — desde JDK 23 el `javac` ya no corre
   procesadores de anotaciones implícitamente. Sin esto, **Lombok** no genera getters/setters/
   constructores y la compilación falla con cientos de "symbol not found".
2. **`spring-boot-flyway`** — en Boot 4 la autoconfiguración de Flyway se movió a un módulo aparte.
   Sin esta dependencia (además de `flyway-core` + `flyway-database-postgresql`), las migraciones no
   corren y el arranque falla con `ddl-auto: validate`.
3. **`spring-boot-webmvc-test`** — en Boot 4 el slice de tests web (`@WebMvcTest`,
   `@AutoConfigureMockMvc`, `MockMvc`) se separó a este módulo. Sin él, los tests de controller no
   compilan.

## Correr en local

```bash
# 1) Postgres en Docker (solo la base)
docker compose up -d db

# 2) Build (corre tests; requiere Docker arriba para los e2e)
mvn clean package

# 3) Arrancar la app con el perfil local
SPRING_PROFILES_ACTIVE=local java -jar target/filgrama-backend-0.1.0-SNAPSHOT.jar
```

Alternativa todo-en-Docker (Postgres + app): `docker compose up --build`.

Probar que está vivo:

```bash
curl http://localhost:8080/api/v1/health
# {"status":"UP","service":"filgrama-backend",...}
```

Login con el admin sembrado en dev (ver "Perfiles" abajo):

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@filgrama.local","password":"Admin123!"}'
```

## Perfiles y seed de admin

| Perfil           | Cuándo                          | Seed admin (`AdminSeedRunner`) | Secretos                                  |
|------------------|---------------------------------|--------------------------------|-------------------------------------------|
| *default* (none) | tests e2e/integración           | **Sí** (gateado `!prod`)       | defaults DEV de `application.yml`          |
| `local`          | desarrollo local                | **Sí**                         | defaults DEV de `application.yml`          |
| `test`           | tests con `@ActiveProfiles`     | **Sí**                         | defaults DEV de `application.yml`          |
| `prod`           | producción                      | **No** — nunca se carga        | **obligatorios por env** (boot falla si no) |

- El **seed de admin** (`admin@filgrama.local` / `Admin123!`) está gateado con `@Profile("!prod")`:
  corre en todo perfil que no sea `prod` (incluido el perfil *default* que usan los e2e), y **nunca**
  en `prod`. Se usa denylist (`!prod`) y no allowlist (`{local,test}`) a propósito: los e2e arrancan
  sin perfil activo, así que un allowlist los dejaría sin admin y romperían con 401 en el login.
- En `prod`, además, **proveedores reales** de OAuth/insights reemplazan a los mocks
  (`@Profile("!local & !test")`), y el `ProdSecretsValidator` exige secretos propios (abajo).

### Crear el primer admin en prod

Como el seed no corre en `prod`, el primer admin se crea fuera de la app, por ejemplo:

- **Comando one-shot / migración de datos de deploy** que inserte una fila en `users` con
  `role = ADMIN`, `active = true` y un `password_hash` BCrypt generado aparte; **o**
- arrancar **una sola vez** con `SPRING_PROFILES_ACTIVE=prod,local` (o un perfil `bootstrap`
  dedicado) para forzar el seed, loguearse, **cambiar la contraseña** y volver a arrancar en `prod`
  puro. (Menos recomendado: deja la credencial conocida creada.)

La opción limpia para deploy es el inserto one-shot con un hash propio; la implementación concreta
queda del lado de deploy/infra.

## Variables de entorno sensibles para prod

En `prod` el arranque **aborta con mensaje claro** si cualquiera de estos secretos sigue siendo el
default de desarrollo (commiteado en `application.yml`) o está vacío — lo valida
`com.filgrama.auth.ProdSecretsValidator`. Setealas con valores propios, **no commiteados**:

| Env var                          | Config                          | Qué protege                                  |
|----------------------------------|---------------------------------|----------------------------------------------|
| `SECURITY_JWT_SECRET`            | `security.jwt.secret`           | Firma HS256 del JWT (≥32 bytes)              |
| `SECURITY_TOKEN_ENCRYPTION_KEY`  | `security.token-encryption-key` | Cifrado AES-GCM de tokens OAuth (base64 32B) |
| `OAUTH_STATE_SECRET`             | `oauth.state-secret`            | Firma HMAC del `state` OAuth (base64 ≥32B)   |

Generar claves base64 de 32 bytes: `openssl rand -base64 32`.

Otras env vars (no validadas como fail-fast, pero necesarias en prod): `DB_URL`, `DB_USER`,
`DB_PASSWORD`, las credenciales de apps OAuth (`META_APP_ID`/`META_APP_SECRET`,
`TIKTOK_CLIENT_KEY`/`TIKTOK_CLIENT_SECRET`) y el storage S3/R2 (`STORAGE_S3_*`, `STORAGE_BUCKET`).
Todas son override-ables por env (`${ENV:default}` en `application.yml`).

## Estructura

```
backend/
├── pom.xml                                # Boot 4.0.0, JDK 25, los 3 fixes de infra
├── Dockerfile
├── docker-compose.yml
└── src/main/
    ├── java/com/filgrama/
    │   ├── FilgramaApplication.java        # main
    │   ├── auth/                           # JWT, login/refresh, seed admin, fail-fast de secretos
    │   ├── oauth/                          # onboarding OAuth + cifrado de tokens
    │   ├── sync/                           # job diario de captura de métricas
    │   ├── reports/                        # generación de reportes
    │   ├── error/GlobalExceptionHandler.java  # RFC 7807, único advice
    │   └── config/SecurityConfig.java      # cadena de filtros JWT
    └── resources/
        ├── application.yml                 # config + perfiles + secretos override-ables por env
        └── db/migration/                   # esquema (Flyway)
```

## Notas

- El esquema lo maneja **Flyway** (`ddl-auto: validate`); no tocar la base a mano.
- Errores en formato **RFC 7807** (`application/problem+json`) vía `GlobalExceptionHandler`. Los
  tracks lanzan `ApiException`; 401/403 se formatean en la cadena de seguridad.
</content>
