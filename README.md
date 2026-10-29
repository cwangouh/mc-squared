# Formula Trainer Backend

Backend for the Formula Trainer MVP.

## Stack

- Java 21 and Spring Boot
- PostgreSQL, Flyway, and Spring Data JPA
- Maven Wrapper
- Docker Compose and Testcontainers

## Local Development

Create the local environment file:

```bash
cp .env.example .env
```

Start PostgreSQL and the application in Docker:

```bash
docker compose up --build
```

Alternatively, start only PostgreSQL and run Spring Boot locally:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`. Check it with:

```bash
curl http://localhost:8080/api/v1/health
```

Expected response:

```json
{"status":"UP"}
```

Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`.

PostgreSQL is exposed on host port `55432` by default to avoid conflicts with a locally installed database on `5432`. Uploaded images are stored locally under `MEDIA_STORAGE_PATH` from `.env`.

Flyway owns the database schema. Add a migration under `src/main/resources/db/migration` for every schema change; Hibernate DDL generation is disabled.

## Tests

Docker must be running because integration tests use PostgreSQL through Testcontainers.

```bash
./mvnw test
```

## Formatting

Format the Java sources:

```bash
./mvnw formatter:format
```

Check formatting without changing files:

```bash
./mvnw formatter:validate
```

## Deployment

Production setup and CI/CD are documented in [DEPLOYMENT.md](./DEPLOYMENT.md).
