# Fil-Grama — Backend

Backend de la plataforma analítica de redes (Fil-Grama). Spring Boot 4.0 · Java 25 · PostgreSQL.

> Este es el **bootstrap**: el proyecto compila, levanta, se conecta a Postgres y crea el esquema
> núcleo (Flyway). La lógica de OAuth, job diario, métricas y reportes se construye después, sobre
> esta base. La fuente de verdad del diseño está en `../spec/`.

## Requisitos

- Docker + Docker Compose (camino recomendado, no necesitás Java/Maven local), **o**
- Java 25 + Maven 3.9+ para correr sin Docker.

## Levantar con Docker (recomendado)

```bash
cp .env.example .env        # ajustá DB_PASSWORD
docker compose up --build
```

Esto levanta Postgres + la app, corre las migraciones Flyway y expone el backend en
`http://localhost:8080`.

Probar que está vivo:

```bash
curl http://localhost:8080/api/v1/health
# {"status":"UP","service":"filgrama-backend",...}
```

## Levantar sin Docker

```bash
# Tener un Postgres local con la base 'filgrama' creada, luego:
export DB_URL=jdbc:postgresql://localhost:5432/filgrama
export DB_USER=filgrama
export DB_PASSWORD=filgrama
mvn spring-boot:run
```

## Estructura

```
backend/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── src/main/
    ├── java/com/filgrama/
    │   ├── FilgramaApplication.java      # main
    │   ├── config/SecurityConfig.java    # esqueleto (TODO: JWT)
    │   └── web/HealthController.java      # /api/v1/health
    └── resources/
        ├── application.yml                # config + perfiles
        └── db/migration/
            └── V1__core_schema.sql        # esquema núcleo (Flyway)
```

## Notas

- El esquema lo maneja **Flyway** (`ddl-auto: validate`); no tocar la base a mano.
- Seguridad: hoy es permisiva (bootstrap). Implementar **JWT** según `spec/03-contratos-api.md`
  y `spec/09-flujo-oauth.md` antes de exponer nada real.
- Próximos pasos: entidades JPA + repositorios, auth JWT, onboarding OAuth, job diario, reportes.
