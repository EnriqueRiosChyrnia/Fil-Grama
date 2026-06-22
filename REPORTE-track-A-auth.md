# Definición de TERMINADO — Track A (Auth/JWT) · rama `feat/auth`

Autenticación JWT implementada completa y **verificada end-to-end contra un Postgres real**:
login, refresh con rotación + detección de reuso, logout, `/me`, RBAC por método, errores RFC 7807.
Rebase sobre `main` hecho (estaba al día: `feat/auth` == `main` == `c298d3e`).

---

## ⚠️ BLOQUEANTES de infra compartida (pom.xml — para la CENTRAL)

El backend **no compila ni arranca con el `pom.xml` actual** por 2 defectos de build heredados del
bootstrap. **Afectan a TODOS los tracks, no solo a Auth.** No los toqué porque `pom.xml` es de la
central (regla de coordinación: parar y pedir cambios de dependencia). Un 3.º afecta solo a tests.

| # | Síntoma | Causa | Fix en `pom.xml` (central) |
|---|---------|-------|----------------------------|
| 1 | **Nada compila** — faltan todos los getters/setters Lombok (incluido `com.filgrama.domain.User` de la central) | Desde **JDK 23** `javac` ya no corre annotation processors del classpath por defecto. Con Java 25 Lombok no se ejecuta. | En `maven-compiler-plugin` agregar `<proc>full</proc>` (o `annotationProcessorPaths` con lombok). |
| 2 | **La app no arranca** — Hibernate `validate` falla con `missing table [account_credentials]` | Boot 4 modularizó los autoconfig. El pom trae `flyway-core` pero **no** el módulo `spring-boot-flyway`, así que **Flyway nunca corre** y las migraciones no se aplican. | Agregar dependency `org.springframework.boot:spring-boot-flyway`. |
| 3 | (solo tests) `@WebMvcTest` no existe en el classpath de test | Boot 4 lo movió a `spring-boot-starter-webmvc-test`, ausente del pom. | (opcional, test scope) `org.springframework.boot:spring-boot-starter-webmvc-test`. |

**Interino que usé para verificar mi entrega sin tocar `pom.xml`:**
- Compilar/test: `mvn clean package -Dmaven.compiler.proc=full` (flag de CLI, no edita el pom).
- Correr la app: levanté Postgres efímero y **apliqué V1+V2 a mano** (workaround de #2), luego corrí el jar.

Con el pom corregido por la central, el comando del DoD será `mvn clean package` sin flags y la app
arrancará aplicando V1+V2 por Flyway automáticamente.

---

## 1. Build + tests

```
mvn clean package -Dmaven.compiler.proc=full
→ BUILD SUCCESS · Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
```

## 2. Tests automatizados (27)

Por restricción del bloqueante #3, la cadena de seguridad se cubre a **nivel de componente** (no slice MVC):

| Suite | Casos cubiertos |
|---|---|
| `JwtServiceTest` (4) | emite/parsea HS256; token expirado→rechazo; token alterado→rechazo; firma con otro secreto→rechazo |
| `RefreshTokenServiceTest` (6) | emisión guarda **hash** (no el claro); rotación revoca viejo + setea `replaced_by`; **reuso→revoca familia**; expirado→error; desconocido→error; logout revoca |
| `AuthServiceTest` (6) | login OK; password mala→`InvalidCredentials`; **usuario inactivo→rechazo**; inexistente→rechazo; `/me` OK; `/me` inexistente→`ApiException`(404) |
| `JwtAuthenticationFilterTest` (3) | Bearer válido puebla `Authentication` con `ROLE_<role>`; sin header no autentica; token inválido no autentica |
| `SecurityProblemHandlersTest` (2) | entry point→**401** problem+json; access denied→**403** problem+json |
| `AuthControllerTest` (6) | login 200/401; refresh 200/401; logout 204; `/me` |

**RBAC `EMPLEADO`→403:** el endpoint `@PreAuthorize("hasRole('ADMIN')")` lo crean B/C/D. Lo dejo cubierto
por partes: el filtro asigna `ROLE_<role>`, `@EnableMethodSecurity` está activo, y el `AccessDeniedHandler`
emite 403 problem+json (test). El slice MVC end-to-end requiere el módulo del bloqueante #3.

## 3. Prueba manual (curl) — ejecutada en vivo contra Postgres real

Postgres efímero en `:5544` (V1+V2 aplicadas a mano), jar en `:8081`. Resultados reales:

| # | Caso | Resultado |
|---|------|-----------|
| 1 | `POST /auth/login` admin OK | **200** `{accessToken, refreshToken, user{id,email,fullName,role}}` (sin `password_hash`) |
| — | header del JWT | `{"alg":"HS256"}` ✓ |
| 2 | `GET /auth/me` con Bearer | **200** `{id,email,fullName,role}` |
| 3 | `GET /auth/me` sin token | **401** `application/problem+json` (`type/title/status/detail/instance`) |
| 4 | `POST /auth/login` password mala | **401** |
| 5 | `POST /auth/login` usuario inactivo | **401** |
| 6 | `POST /auth/refresh` válido | **200** nuevo par (rotación) |
| 7 | `POST /auth/refresh` **reusando** el viejo | **401** + **revoca toda la familia** |
| 8 | `POST /auth/refresh` con el token "nuevo" tras el reuso | **401** (la familia entera quedó muerta) ✓ |
| 9 | `POST /auth/logout` (Bearer + refresh) | **204**; refresh posterior → **401** |
| 10 | `POST /auth/login` body inválido | **400** problem+json **vía el advice global** `com.filgrama.error` (no creé otro) |

Comando base de la prueba:
```bash
curl -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@filgrama.local","password":"Admin123!"}'
# copiar accessToken →
curl localhost:8080/api/v1/auth/me -H "Authorization: Bearer <accessToken>"
```

## 4. Archivos creados / modificados

**Modificado (1):** `com/filgrama/config/SecurityConfig.java` (esqueleto → seguridad real JWT stateless).

**Creados — main (21, en `com.filgrama.auth.**`):**
```
auth/JwtProperties.java               auth/JwtService.java
auth/AuthService.java                 auth/InvalidCredentialsException.java
auth/AdminSeedRunner.java
auth/security/JwtAuthenticationFilter.java   auth/security/AuthUserDetails.java
auth/security/DbUserDetailsService.java      auth/security/ProblemSupport.java
auth/security/RestAuthenticationEntryPoint.java  auth/security/RestAccessDeniedHandler.java
auth/token/RefreshToken.java          auth/token/RefreshTokenRepository.java
auth/token/RefreshTokenService.java   auth/token/RefreshTokenException.java
auth/web/AuthController.java
auth/web/dto/{LoginRequest,LoginResponse,RefreshRequest,RefreshResponse,UserDto}.java
```
**Creada — migración (1):** `db/migration/V2__auth_refresh_tokens.sql` (tabla `refresh_tokens` + índices).

**Creados — test (6, bajo `com.filgrama.auth.**`):** `JwtServiceTest`, `AuthServiceTest`,
`token/RefreshTokenServiceTest`, `security/JwtAuthenticationFilterTest`,
`security/SecurityProblemHandlersTest`, `web/AuthControllerTest`.

## 5. Claves de config para `application.yml` (que agregue la CENTRAL) + credencial seed

Leídas con `@ConfigurationProperties(prefix="security.jwt")` (`JwtProperties`), con defaults de dev y
**env-overridable**. La central puede agregarlas a `application.yml` así:

```yaml
security:
  jwt:
    secret: ${SECURITY_JWT_SECRET:<>=32 bytes; en prod por env>}   # HS256
    access-ttl: ${SECURITY_JWT_ACCESS_TTL:15m}
    refresh-ttl: ${SECURITY_JWT_REFRESH_TTL:30d}
```
- `secret` debe tener **≥ 32 bytes** (HS256). El default de dev en `JwtProperties` es inseguro a propósito.

**Admin seed (DEV, removible)** — `AdminSeedRunner` (idempotente, `CommandLineRunner`):
- credencial: **`admin@filgrama.local` / `Admin123!`** (rol `ADMIN`).
- Es solo para probar Auth sin depender del CRUD de usuarios (track B). Borrar la clase o gatearla por
  perfil cuando B provea alta de usuarios.

## 6. Confirmación de alcance

Toqué **solo**: `com.filgrama.auth.**`, `com.filgrama.config.SecurityConfig`, `V2__auth_refresh_tokens.sql`
y tests bajo `com.filgrama.auth.**`. **No toqué**: `pom.xml`, `application.yml`, `com.filgrama.domain.**`,
`com.filgrama.repository.**`, `com.filgrama.error.**`, `V1`. Reutilicé el advice global
`com.filgrama.error.GlobalExceptionHandler` (verificado: el 400 de validación sale por él) y **no** creé
otro `@RestControllerAdvice`. Los 401/403 los formatea Auth (entry point / access denied + el controller),
con el mismo formato problem+json.
